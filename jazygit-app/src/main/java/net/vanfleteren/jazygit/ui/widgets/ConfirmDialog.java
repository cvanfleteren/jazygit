package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.popup.ConfirmablePopup;
import net.vanfleteren.jazygit.state.popup.Popup;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.ui.TextWidth;

/**
 * The standard popup asking to confirm an action: a title, a message, and a hint, by default that Enter
 * confirms and Escape cancels. Whether it is open lives in the {@link Model}, as a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class ConfirmDialog<P extends Popup> implements PopupView {

    private static final int WIDTH = 60;
    // As wide as a word that does not fit asks, such as a path, but no wider than this.
    private static final int MAX_WIDTH = 100;
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
            int width = width(text);
            int messageHeight = DialogFrame.wrappedHeight(text, width, Integer.MAX_VALUE);
            return DialogFrame.withHint(
                            spec.title().apply(popup),
                            width,
                            spec.hintKey(),
                            List.of(DialogFrame.wrapped(text, width, Integer.MAX_VALUE)))
                    .id(spec.id())
                    .focusable()
                    .length(messageHeight + EXTRA_HEIGHT)
                    .onConfirm(() -> dispatch.accept(spec.onConfirm().get()))
                    .onCancel(() -> dispatch.accept(spec.onCancel().get()));
        });
    }

    /**
     * The width that shows {@code message}: {@value #WIDTH}, or wider to keep a longer word, such as a path, on one
     * line, up to {@value #MAX_WIDTH}.
     */
    static int width(String message) {
        int longestWord =
                List.of(message.split("\\s+")).map(TextWidth::of).max().getOrElse(0);
        return Math.clamp(DialogFrame.FRAME + longestWord, WIDTH, MAX_WIDTH);
    }

    /**
     * What the dialog shows and sends.
     *
     * @param id        the id of the dialog, which takes the focus while it is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param message   the question, from the popup; it is wrapped at the dialog's width
     * @param onConfirm the message for Enter
     * @param onCancel  the message for Escape; by default a {@link PopupMsg.Dismissed}
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
                    () -> new PopupMsg.Dismissed(popupType),
                    "dialog.hint.confirmCancel");
        }

        /**
         * A dialog for a popup that asks about one operation, which Enter starts and Escape only closes (ADR 38).
         */
        public static <P extends ConfirmablePopup> Spec<P> confirming(
                String id, Class<P> popupType, Function<P, String> title, Function<P, String> message) {
            return new Spec<>(id, popupType, title, message, () -> new PopupMsg.Confirmed(popupType));
        }

        /**
         * This dialog, sending {@code onCancel} for Escape, for a popup that does more than close then.
         */
        public Spec<P> cancelledBy(Supplier<Msg> onCancel) {
            return new Spec<>(id, popupType, title, message, onConfirm, onCancel, hintKey);
        }
    }
}
