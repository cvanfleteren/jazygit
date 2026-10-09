package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Popup asking for a commit message: a summary and a description. Tab switches between them. Enter
 * in the summary, or Ctrl+Enter in the description, confirms; Escape cancels. Whether it is open
 * lives in the {@link Model}; the text being typed lives in the input widgets.
 *
 * <p>There are two flavours: {@link #commit} asks for the message of a new commit, {@link #reword}
 * for a new message of the last commit, starting out with the current one.
 */
public final class CommitDialog {

    /**
     * The id of the summary field of the commit popup, which gets the focus when the popup opens.
     */
    public static final String ID = "commit-summary";
    public static final String DESCRIPTION_ID = "commit-description";
    public static final String REWORD_ID = "reword-summary";
    public static final String REWORD_DESCRIPTION_ID = "reword-description";

    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int DESCRIPTION_HEIGHT = 10;

    private final TextInputState summary = new TextInputState();
    private final TextAreaState description = new TextAreaState();
    private final String summaryId;
    private final String descriptionId;
    // The commit whose message the popup starts out with, while it is open.
    private final Function<Model, Optional<Commit>> current;
    private final Predicate<Model> open;
    private final BiFunction<String, String, Msg> confirmed;
    private final Supplier<Msg> cancelled;
    private final Consumer<Msg> dispatch;
    private boolean shown;

    private CommitDialog(String summaryId, String descriptionId, Predicate<Model> open,
                         Function<Model, Optional<Commit>> current, BiFunction<String, String, Msg> confirmed,
                         Supplier<Msg> cancelled, Consumer<Msg> dispatch) {
        this.summaryId = summaryId;
        this.descriptionId = descriptionId;
        this.open = open;
        this.current = current;
        this.confirmed = confirmed;
        this.cancelled = cancelled;
        this.dispatch = dispatch;
    }

    /**
     * The popup asking for the message of a new commit.
     *
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitDialog(Consumer<Msg> dispatch) {
        this(ID, DESCRIPTION_ID, Model::commitOpen, model -> Optional.empty(), Msg.CommitConfirmed::new,
                Msg.CommitCancelled::new, dispatch);
    }

    /**
     * The popup asking for a new message of the last commit.
     *
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static CommitDialog reword(Consumer<Msg> dispatch) {
        return new CommitDialog(REWORD_ID, REWORD_DESCRIPTION_ID, model -> model.rewording().isPresent(),
                Model::rewording, Msg.RewordConfirmed::new, Msg.RewordCancelled::new, dispatch);
    }

    /**
     * The ids of the fields of the popup; the first one gets the focus when it opens.
     */
    public List<String> ids() {
        return List.of(summaryId, descriptionId);
    }

    /**
     * The popup, while the model asks for a commit message.
     */
    public Optional<Element> render(Model model) {
        if (!open.test(model)) {
            shown = false;
            return Optional.empty();
        }
        if (!shown) {
            shown = true;
            current.apply(model).ifPresent(commit -> {
                summary.setText(commit.message());
                description.setText(commit.body());
            });
        }
        return Optional.of(dialog(
                        textInput(summary)
                                .id(summaryId)
                                .title("Commit summary")
                                .rounded()
                                .placeholder("summary")
                                .length(INPUT_HEIGHT)
                                .onSubmit(this::confirm),
                        new SubmittableTextArea(textArea(description)
                                .title("Commit description")
                                .placeholder("ctrl+enter to confirm")
                                .rounded(), descriptionId, this::confirm)
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
        dispatch.accept(confirmed.apply(message, details));
    }

    private void cancel() {
        clear();
        dispatch.accept(cancelled.get());
    }

    private void clear() {
        summary.clear();
        description.clear();
    }
}
