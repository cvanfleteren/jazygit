package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup asking for the commit message: a summary and a description. Tab switches between them. Enter
 * in the summary, or Ctrl+Enter in the description, commits; Escape cancels. Whether it is open
 * lives in the {@link Model}; the text being typed lives in the input widgets.
 */
public final class CommitDialog {

    /**
     * The id of the summary field, which gets the focus when the popup opens.
     */
    public static final String ID = "commit-summary";
    public static final String DESCRIPTION_ID = "commit-description";

    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int DESCRIPTION_HEIGHT = 10;

    private final TextInputState summary = new TextInputState();
    private final TextAreaState description = new TextAreaState();
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks for a commit message.
     */
    public Optional<Element> render(Model model) {
        return Optional.of(model).filter(Model::commitOpen).map(m -> dialog(
                        textInput(summary)
                                .id(ID)
                                .title("Commit summary")
                                .rounded()
                                .placeholder("summary")
                                .length(INPUT_HEIGHT)
                                .onSubmit(this::confirm),
                        new SubmittableTextArea(textArea(description)
                                .title("Commit description")
                                .placeholder("ctrl+enter to commit")
                                .rounded(), DESCRIPTION_ID, this::confirm)
                                .rows(DESCRIPTION_HEIGHT))
                .borderType(BorderType.NONE)
                .width(WIDTH)
                .onCancel(this::cancel));
    }

    private void confirm() {
        String message = summary.text();
        String details = description.text();
        // A blank summary keeps the popup open, see Update.
        if (!message.isBlank()) {
            clear();
        }
        dispatch.accept(new Msg.CommitConfirmed(message, details));
    }

    private void cancel() {
        clear();
        dispatch.accept(new Msg.CommitCancelled());
    }

    private void clear() {
        summary.clear();
        description.clear();
    }
}
