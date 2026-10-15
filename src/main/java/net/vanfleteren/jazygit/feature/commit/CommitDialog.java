package net.vanfleteren.jazygit.feature.commit;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Popup asking for a commit message: a summary and a description. Tab switches between them. Enter
 * in the summary, or Ctrl+Enter in the description, confirms; Escape cancels. Whether it is open
 * lives in the {@link Model}; the text being typed lives in the input widgets.
 *
 * <p>There are two flavours: {@link #commit} asks for the message of a new commit, {@link #reword}
 * for a new message of any commit, starting out with its current one.
 */
public final class CommitDialog implements PopupView {

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
    private final Function<Model, Option<Commit>> current;
    private final Predicate<Model> open;
    private final Function<CommitMessage, Msg> confirmed;
    private final Supplier<Msg> cancelled;
    private final Consumer<Msg> dispatch;
    private Option<Popup> shownFor = Option.none();

    private CommitDialog(String summaryId, String descriptionId, Predicate<Model> open,
                         Function<Model, Option<Commit>> current, Function<CommitMessage, Msg> confirmed,
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
        this(ID, DESCRIPTION_ID, model -> model.popup(CommitPopup.class).isDefined(), model -> Option.none(), CommitMsg.Confirmed::new,
                CommitMsg.Cancelled::new, dispatch);
    }

    /**
     * The popup asking for a new message of the last commit.
     *
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static CommitDialog reword(Consumer<Msg> dispatch) {
        return new CommitDialog(REWORD_ID, REWORD_DESCRIPTION_ID, model -> model.popup(RewordPopup.class).isDefined(),
                model -> model.popup(RewordPopup.class).map(RewordPopup::commit), RewordMsg.Confirmed::new, RewordMsg.Cancelled::new, dispatch);
    }

    /**
     * The ids of the fields of the popup; the first one gets the focus when it opens.
     */
    @Override
    public List<String> ids() {
        return List.of(summaryId, descriptionId);
    }

    /**
     * The popup, while the model asks for a commit message.
     */
    @Override
    public Option<Element> render(Model model) {
        Option<Popup> popup = model.popup().filter(p -> open.test(model));
        boolean opened = popup.isDefined() && !shownFor.equals(popup);
        shownFor = popup;
        if (popup.isEmpty()) {
            return Option.none();
        }
        if (opened) {
            current.apply(model).forEach(commit -> {
                summary.setText(commit.message());
                summary.moveCursorToEnd();
                description.setText(commit.body());
                description.moveCursorToEnd();
            });
        }
        return Option.of(dialog(
                textInput(summary)
                        .id(summaryId)
                        .title(Messages.get("dialog.commit.summary.title"))
                        .placeholder(Messages.get("dialog.commit.summary.placeholder"))
                        .length(INPUT_HEIGHT)
                        .onSubmit(this::confirm),
                new SubmittableTextArea(textArea(description)
                        .title(Messages.get("dialog.commit.description.title"))
                        .placeholder(Messages.get("dialog.commit.description.placeholder")),
                        descriptionId, this::confirm)
                        .rows(DESCRIPTION_HEIGHT))
                .borderType(BorderType.NONE)
                .width(WIDTH)
                .onCancel(this::cancel));
    }

    private void confirm() {
        String message = summary.text();
        String details = description.text();
        // A blank summary keeps the popup open, see CommitUpdate and RewordUpdate.
        if (!message.isBlank()) {
            clear();
        }
        dispatch.accept(confirmed.apply(new CommitMessage(message, details)));
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
