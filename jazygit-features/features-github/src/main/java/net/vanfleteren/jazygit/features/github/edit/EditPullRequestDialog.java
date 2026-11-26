package net.vanfleteren.jazygit.features.github.edit;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.BasesField;
import net.vanfleteren.jazygit.kernel.ui.widgets.BodyField;
import net.vanfleteren.jazygit.kernel.ui.widgets.CheckBox;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.FormKeys;
import net.vanfleteren.jazygit.kernel.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.TitleField;

/**
 * Popup editing the pull request that is shown (ADR 59), laid out and keyed as the one that creates one: the list of
 * branches it can merge into, its title, its body, and whether it is a draft. Tab goes from one part to the next; the
 * focus starts in the title. A base or draft box that cannot be changed is shown dimmed, and Tab goes past it. Enter
 * in the title, or Ctrl+S anywhere, saves what was changed; Ctrl+O opens the body in the editor; Escape clears the
 * filter of the bases, or else goes back to the pull request. While it is being saved, it says so; when saving
 * failed, it says why, under the fields. What is picked, typed and ticked lives in the {@link Model}, as an
 * {@link EditPullRequestPopup}, which every change is sent to; the fields only keep where their cursor is.
 */
public final class EditPullRequestDialog implements PopupView {

    /**
     * The id of the title field, which gets the focus when the popup opens.
     */
    public static final String ID = "edit-pull-request-title";

    public static final String BASES_ID = "edit-pull-request-bases";
    public static final String BODY_ID = "edit-pull-request-body";
    public static final String DRAFT_ID = "edit-pull-request-draft";

    // With Ctrl held, opens the body in the editor.
    private static final char EDITOR_KEY = 'o';
    private static final int WIDTH = 110;
    private static final int ERROR_LINES = 3;

    private final TitleField title = new TitleField(
            ID, "github.dialog.newPullRequest.titleField", "github.dialog.newPullRequest.titlePlaceholder");
    private final BodyField body = new BodyField(
            BODY_ID, "github.dialog.newPullRequest.body", "github.dialog.editPullRequest.bodyPlaceholder");
    private final BasesField<RemoteBranch> bases;
    private final FormKeys keys;
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public EditPullRequestDialog(Consumer<Msg> dispatch) {
        this.bases = new BasesField<>(BASES_ID, base -> base.branch().value(), dispatch);
        // A blank title keeps the popup open, see EditPullRequestUpdate.
        this.keys = new FormKeys(
                () -> dispatch.accept(new EditPullRequestMsg.Cancelled()),
                () -> dispatch.accept(new EditPullRequestMsg.Confirmed()),
                this::editorKey);
        this.dispatch = dispatch;
    }

    /**
     * The ids of the parts of the popup; the title gets the focus when it opens.
     */
    @Override
    public List<String> ids() {
        return List.of(ID, BASES_ID, BODY_ID, DRAFT_ID);
    }

    /**
     * The popup, while the model edits a pull request.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(EditPullRequestPopup.class).map(popup -> dialog(model, popup));
    }

    /**
     * The popup editing the pull request of {@code popup}. The fields show what the model has: what was typed in them,
     * or what came back from the editor. Under them, the error of saving it, or of the editor; a new try clears it.
     */
    private Element dialog(Model model, EditPullRequestPopup popup) {
        PullRequestFields fields = popup.fields();
        List<Element> content = List.of(
                        base(model, popup),
                        title.render(fields.title(), keys, text -> edited(fields.draft())),
                        body.render(fields.body(), keys, text -> edited(fields.draft())),
                        draft(popup))
                .appendAll(model.outcome()
                        .error()
                        .filter(error -> !popup.saving())
                        .map(error -> DialogFrame.wrapped(ErrorText.of(error), WIDTH, ERROR_LINES)
                                .addClass(Theme.Classes.ERROR)));
        return new HintedDialog(
                DialogFrame.of(
                        Messages.get(
                                "github.dialog.editPullRequest.title",
                                String.valueOf(popup.number().value()),
                                popup.pullRequestPopup().branch().value()),
                        WIDTH,
                        content.toJavaArray(Element[]::new)),
                Messages.get(
                        popup.saving()
                                ? "github.dialog.editPullRequest.saving"
                                : "github.dialog.editPullRequest.hint"));
    }

    /**
     * The list of the bases, or the base dimmed, with why, when it cannot be changed: a pull request stacked on
     * another merges into the branch of that one, and a closed or merged one keeps its base.
     */
    private Element base(Model model, EditPullRequestPopup popup) {
        RemoteBranch base = popup.fields().base();
        return popup.baseChangeable()
                ? bases.render(popup.bases(), base, model.listCursor(), EditPullRequestMsg.BaseMoved::new, keys::handle)
                : bases.fixed(
                        base,
                        Messages.get(
                                popup.pullRequestPopup().open()
                                        ? "github.dialog.editPullRequest.baseStacked"
                                        : "github.dialog.editPullRequest.baseClosed"));
    }

    /**
     * The draft box; dimmed and without the focus when it cannot be changed.
     */
    private Element draft(EditPullRequestPopup popup) {
        boolean ticked = popup.fields().draft();
        StyledElement<?> label = CheckBox.label(Messages.get("github.dialog.newPullRequest.draft"));
        return CheckBox.line(
                popup.draftChangeable()
                        ? List.of(CheckBox.of(DRAFT_ID, ticked, () -> edited(!ticked), keys), label)
                        : List.of(CheckBox.fixed(ticked), label.addClass(Theme.Classes.DIM)),
                List.of(DRAFT_ID));
    }

    /**
     * Opens the body in the editor on {@link #EDITOR_KEY}, after the keys of every field.
     */
    private EventResult editorKey(KeyEvent event) {
        if (event.hasCtrl() && event.isChar(EDITOR_KEY)) {
            dispatch.accept(new EditPullRequestMsg.EditorRequested());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Tells the model what the pull request says now. The text comes from the fields, which hold every key typed so
     * far, also those typed since the model was last rendered.
     */
    private void edited(boolean draft) {
        dispatch.accept(new EditPullRequestMsg.Edited(title.text(), body.text(), draft));
    }
}
