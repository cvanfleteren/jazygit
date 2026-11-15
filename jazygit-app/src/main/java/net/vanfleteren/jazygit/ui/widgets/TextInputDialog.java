package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.textInput;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextInputElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.widgets.input.TextInputState;
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
import net.vanfleteren.jazygit.state.popup.TextPopup;
import net.vanfleteren.jazygit.ui.edit.LineEditInput;

/**
 * The standard popup asking for one line of text. Enter confirms it, Escape cancels. Whether it is open, and the
 * text as far as it was typed, live in the {@link Model}, as a popup of type {@code P}: the input shows the popup's
 * text, and sends a {@link PopupMsg.TextEdited} for every change. Only the input's caret is kept here.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class TextInputDialog<P extends TextPopup> implements PopupView {

    /**
     * The width of a dialog that shows branch names, in its title or its input, borders included: wide enough for
     * long ones.
     */
    public static final int BRANCH_NAME_WIDTH = 80;

    /**
     * The width of the dialog, borders included, unless its spec asks for another.
     */
    private static final int WIDTH = 50;

    private final Spec<P> spec;
    private final Consumer<Msg> dispatch;
    private final TextInputState input = new TextInputState();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public TextInputDialog(Spec<P> spec, Consumer<Msg> dispatch) {
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
        return model.popup(spec.popupType()).map(open -> {
            // The input shows what the model has: what was typed in it, or what the popup opened with.
            if (!input.text().equals(open.text())) {
                input.setText(open.text());
                input.moveCursorToEnd();
            }
            Element dialog = DialogFrame.of(spec.title().apply(open), spec.width(), field())
                    .onConfirm(this::confirm)
                    .onCancel(this::cancel);
            // The details go in a box of their own under it, as those of the options of a choice do.
            Option<String> details = spec.details().apply(open);
            return details.isEmpty() ? dialog : new WithDetails(dialog, details, details.toList());
        });
    }

    private StyledElement<?> field() {
        TextInputElement field = textInput(input)
                .onSubmit(this::confirm)
                .onKeyEvent(event -> event.isCancel() ? cancelled() : EventResult.UNHANDLED);
        return new LineEditInput(spec.placeholder().map(field::placeholder).getOrElse(field), input, spec.id())
                .onChange(text -> dispatch.accept(new PopupMsg.TextEdited(text)));
    }

    private void confirm() {
        dispatch.accept(spec.onConfirm().get());
    }

    private EventResult cancelled() {
        cancel();
        return EventResult.HANDLED;
    }

    private void cancel() {
        dispatch.accept(spec.onCancel().get());
    }

    /**
     * What the dialog shows and sends.
     *
     * @param id          the id of the input, which takes the focus while the dialog is open
     * @param popupType   the popup that makes the dialog show
     * @param title       the title, from the popup
     * @param placeholder what the input shows while it is empty, if anything
     * @param onConfirm   the message for Enter; its update reads the text from the popup, and decides whether it
     *                    closes
     * @param onCancel    the message for Escape; by default a {@link PopupMsg.Dismissed}
     * @param width       the width of the dialog, borders included; by default {@value #WIDTH}
     * @param details     what is said in a box under the dialog, from the popup, if anything; by default nothing
     */
    public record Spec<P extends Popup>(
            String id,
            Class<P> popupType,
            Function<P, String> title,
            Option<String> placeholder,
            Supplier<Msg> onConfirm,
            Supplier<Msg> onCancel,
            int width,
            Function<P, Option<String>> details) {

        public Spec(
                String id,
                Class<P> popupType,
                Function<P, String> title,
                Option<String> placeholder,
                Supplier<Msg> onConfirm,
                Supplier<Msg> onCancel) {
            this(id, popupType, title, placeholder, onConfirm, onCancel, WIDTH, popup -> Option.none());
        }

        /**
         * A dialog which Escape only closes (ADR 38).
         */
        public Spec(
                String id,
                Class<P> popupType,
                Function<P, String> title,
                Option<String> placeholder,
                Supplier<Msg> onConfirm) {
            this(id, popupType, title, placeholder, onConfirm, () -> new PopupMsg.Dismissed(popupType));
        }

        /**
         * A dialog for a popup that asks about one operation, which Enter starts with what was typed and Escape only
         * closes (ADR 38).
         */
        public static <P extends ConfirmablePopup> Spec<P> confirming(
                String id, Class<P> popupType, Function<P, String> title, Option<String> placeholder) {
            return new Spec<>(id, popupType, title, placeholder, () -> new PopupMsg.Confirmed(popupType));
        }

        /**
         * This dialog, sending {@code onCancel} for Escape, for a popup that does more than close then.
         */
        public Spec<P> cancelledBy(Supplier<Msg> onCancel) {
            return new Spec<>(id, popupType, title, placeholder, onConfirm, onCancel, width, details);
        }

        /**
         * This dialog, {@code width} wide, borders included.
         */
        public Spec<P> withWidth(int width) {
            return new Spec<>(id, popupType, title, placeholder, onConfirm, onCancel, width, details);
        }

        /**
         * This dialog, saying what {@code details} gives for the open popup, if anything, in a box under it.
         */
        public Spec<P> withDetails(Function<P, Option<String>> details) {
            return new Spec<>(id, popupType, title, placeholder, onConfirm, onCancel, width, details);
        }
    }
}
