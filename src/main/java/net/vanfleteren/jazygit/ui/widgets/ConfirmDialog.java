package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

    /**
     * How many lines {@code text} takes when wrapped at {@code width} on spaces: each of its own lines starts a
     * new one, and a word longer than the width is cut over several.
     */
    public static int lines(String text, int width) {
        return List.of(text.split("\\R", -1)).map(line -> wrapped(line, width)).sum().intValue();
    }

    private static int wrapped(String line, int width) {
        record Wrapped(int lines, int used) {

            /**
             * With {@code word} starting at the beginning of the last line.
             */
            Wrapped starting(int word, int width) {
                int cut = Math.max(0, word - 1) / width;
                return new Wrapped(lines + cut, word - cut * width);
            }
        }
        return List.of(line.split(" ")).filter(word -> !word.isEmpty())
                .foldLeft(new Wrapped(1, 0), (wrapped, word) -> wrapped.used() == 0
                        ? wrapped.starting(word.length(), width)
                        : wrapped.used() + 1 + word.length() <= width
                        ? new Wrapped(wrapped.lines(), wrapped.used() + 1 + word.length())
                        : new Wrapped(wrapped.lines() + 1, 0).starting(word.length(), width))
                .lines();
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
            return DialogFrame.withHint(spec.title().apply(popup), WIDTH, spec.hintKey(), List.of(
                            DialogFrame.wrapped(text, WIDTH, Integer.MAX_VALUE)))
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
     * @param onCancel  the message for Escape
     * @param hintKey   the message key of the hint about the keys
     */
    public record Spec<P>(String id, Class<P> popupType, Function<P, String> title, Function<P, String> message,
                          Supplier<Msg> onConfirm, Supplier<Msg> onCancel, String hintKey) {

        /**
         * A dialog with the hint that Enter confirms and Escape cancels.
         */
        public Spec(String id, Class<P> popupType, Function<P, String> title, Function<P, String> message,
                    Supplier<Msg> onConfirm, Supplier<Msg> onCancel) {
            this(id, popupType, title, message, onConfirm, onCancel, "dialog.hint.confirmCancel");
        }
    }
}
