package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.textInput;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.TextInputElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

/**
 * The standard popup asking for one line of text. Enter confirms it, Escape cancels. Whether it is open lives
 * in the {@link Model}, as a popup of type {@code P}; the text being typed lives in the input widget, which
 * starts out with the popup's initial text each time the popup opens.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class TextInputDialog<P extends Popup> implements PopupView {

    private static final int WIDTH = 50;
    private final Spec<P> spec;
    private final Consumer<Msg> dispatch;
    private final TextInputState input = new TextInputState();
    // The popup the input was filled in for, so the initial text is only filled in when it opens.
    private Option<P> shownFor = Option.none();
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
        Option<P> popup = model.popup(spec.popupType());
        popup.filter(open -> !shownFor.contains(open)).forEach(open -> {
            input.setText(spec.initialText().apply(open));
            input.moveCursorToEnd();
        });
        shownFor = popup;
        return popup.map(open -> DialogFrame.of(spec.title().apply(open), WIDTH, field())
                .onConfirm(this::confirm)
                .onCancel(this::cancel));
    }

    private TextInputElement field() {
        TextInputElement field = textInput(input)
                .id(spec.id())
                .onSubmit(this::confirm)
                .onKeyEvent(event -> event.code() == KeyCode.ESCAPE ? cancelled() : EventResult.UNHANDLED);
        return spec.placeholder().map(field::placeholder).getOrElse(field);
    }

    private void confirm() {
        dispatch.accept(spec.onConfirm().apply(input.text()));
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
     * @param initialText the text the input starts out with, from the popup
     * @param onConfirm   the message for Enter, given the text; it decides whether the popup closes
     * @param onCancel    the message for Escape
     */
    public record Spec<P>(
            String id,
            Class<P> popupType,
            Function<P, String> title,
            Option<String> placeholder,
            Function<P, String> initialText,
            Function<String, Msg> onConfirm,
            Supplier<Msg> onCancel) {}
}
