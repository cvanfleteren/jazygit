package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

/**
 * The standard popup asking to confirm an action: a title, a message, and a hint, by default that Enter
 * confirms and Escape cancels. Whether it is open lives in the {@link Model}, as a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class ConfirmDialog<P extends Popup> implements PopupView {

    private static final int WIDTH = 60;
    // The blank line and the hint below the message, plus the top and bottom border.
    private static final int EXTRA_HEIGHT = 2 + 2;
    private final Spec<P> spec;
    private final Consumer<Msg> dispatch;
    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ConfirmDialog(Spec<P> spec, Consumer<Msg> dispatch) {
        this.spec = spec;
        this.dispatch = dispatch;
    }

    @Override
    public List<String> ids() {
        return List.of(spec.id());
    }

    /**
     * The popup, while the model has {@code P} open.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(spec.popupType()).map(popup -> {
            String text = spec.message().apply(popup);
            int messageHeight = DialogFrame.wrappedHeight(text, WIDTH, Integer.MAX_VALUE);
            return DialogFrame.withHint(
                            spec.title().apply(popup),
                            WIDTH,
                            spec.hintKey(),
                            List.of(DialogFrame.wrapped(text, WIDTH, Integer.MAX_VALUE)))
                    .id(spec.id())
                    .focusable()
                    .length(messageHeight + EXTRA_HEIGHT)
                    .onConfirm(() -> dispatch.accept(spec.onConfirm().get()))
                    .onCancel(() -> dispatch.accept(spec.onCancel().get()));
        });
    }

    /**
     * What the dialog shows and sends.
     *
     * @param id        the id of the dialog, which takes the focus while it is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param message   the question, from the popup; it is wrapped at the dialog's width
     * @param onConfirm the message for Enter
     * @param onCancel  the message for Escape; by default a {@link Msg.PopupDismissed}
     * @param hintKey   the message key of the hint about the keys
     */
    public record Spec<P extends Popup>(
            String id,
            Class<P> popupType,
            Function<P, String> title,
            Function<P, String> message,
            Supplier<Msg> onConfirm,
            Supplier<Msg> onCancel,
            String hintKey) {

        /**
         * A dialog with the hint that Enter confirms and Escape cancels, which Escape only closes (ADR 38).
         */
        public Spec(
                String id,
                Class<P> popupType,
                Function<P, String> title,
                Function<P, String> message,
                Supplier<Msg> onConfirm) {
            this(
                    id,
                    popupType,
                    title,
                    message,
                    onConfirm,
                    () -> new Msg.PopupDismissed(popupType),
                    "dialog.hint.confirmCancel");
        }

        /**
         * This dialog, sending {@code onCancel} for Escape, for a popup that does more than close then.
         */
        public Spec<P> cancelledBy(Supplier<Msg> onCancel) {
            return new Spec<>(id, popupType, title, message, onConfirm, onCancel, hintKey);
        }
    }
}
