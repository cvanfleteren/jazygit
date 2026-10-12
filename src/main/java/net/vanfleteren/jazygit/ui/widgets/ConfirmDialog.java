package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.paragraph.Paragraph;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The standard popup asking to confirm an action: a title, a message, and always the same hint that Enter
 * confirms and Escape cancels. Whether it is open lives in the {@link Model}, as a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class ConfirmDialog<P extends Popup> implements PopupView {

    private static final int WIDTH = 60;
    // The popup's border and padding on both sides.
    private static final int INNER_WIDTH = WIDTH - 2 - 2;
    // The blank line and the hint below the message, plus the top and bottom border.
    private static final int EXTRA_HEIGHT = 2 + 2;

    /**
     * What the dialog shows and sends.
     *
     * @param id        the id of the dialog, which takes the focus while it is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param message   the question, from the popup; it is wrapped at the dialog's width
     * @param onConfirm the message for Enter
     * @param onCancel  the message for Escape
     */
    public record Spec<P>(String id, Class<P> popupType, Function<P, String> title, Function<P, String> message,
                          Supplier<Msg> onConfirm, Supplier<Msg> onCancel) {
    }

    private final Spec<P> spec;
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ConfirmDialog(Spec<P> spec, Consumer<Msg> dispatch) {
        this.spec = spec;
        this.dispatch = dispatch;
    }

    public String id() {
        return spec.id();
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
            int messageHeight = lines(text, INNER_WIDTH);
            return DialogFrame.withHint(spec.title().apply(popup), WIDTH, "dialog.hint.confirmCancel", List.of(
                            widget(Paragraph.builder().text(text).overflow(Overflow.WRAP_WORD).build()).length(messageHeight)))
                    .id(spec.id())
                    .focusable()
                    .length(messageHeight + EXTRA_HEIGHT)
                    .onConfirm(() -> dispatch.accept(spec.onConfirm().get()))
                    .onCancel(() -> dispatch.accept(spec.onCancel().get()));
        });
    }

    /**
     * How many lines {@code text} takes when wrapped at {@code width} on spaces.
     */
    static int lines(String text, int width) {
        record Wrapped(int lines, int used) {
        }
        return List.of(text.split(" "))
                .foldLeft(new Wrapped(1, 0), (wrapped, word) -> wrapped.used() > 0 && wrapped.used() + 1 + word.length() > width
                        ? new Wrapped(wrapped.lines() + 1, word.length())
                        : new Wrapped(wrapped.lines(), wrapped.used() + (wrapped.used() > 0 ? 1 : 0) + word.length()))
                .lines();
    }
}
