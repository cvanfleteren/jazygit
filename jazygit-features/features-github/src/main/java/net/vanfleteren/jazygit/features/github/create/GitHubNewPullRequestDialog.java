package net.vanfleteren.jazygit.features.github.create;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
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
 * Popup asking what a new pull request merges into and what it says: the list of branches it can merge into, its
 * title, which starts out as the subject of the oldest commit of the branch not on the base, its body, which starts
 * out empty, and whether it is a draft. Tab goes from one part to the next; the focus starts in the title. In the
 * list, the highlighted branch is the base; the arrows move it, typing filters the list, and Enter goes on to the
 * title. Space or Enter on the draft box ticks it, and on the box that stacks it on the pull request of its base,
 * shown when the base has one (ADR 73). Enter in the title, or Ctrl+S anywhere, creates the pull request;
 * Escape clears the filter, or else cancels. What is picked, typed and ticked lives in the {@link Model}, which every
 * change is sent to; the fields only keep where their cursor is.
 */
public final class GitHubNewPullRequestDialog implements PopupView {

    /**
     * The id of the title field, which gets the focus when the popup opens.
     */
    public static final String ID = "new-pull-request-title";

    public static final String BASES_ID = "new-pull-request-bases";
    public static final String BODY_ID = "new-pull-request-body";
    public static final String DRAFT_ID = "new-pull-request-draft";
    public static final String STACK_ID = "new-pull-request-stack";

    private static final int WIDTH = 110;
    // Between the draft box and the stack box.
    private static final String GAP = "    ";

    private final TitleField title = new TitleField(
            ID, "github.dialog.newPullRequest.titleField", "github.dialog.newPullRequest.titlePlaceholder");
    private final BodyField body =
            new BodyField(BODY_ID, "github.dialog.newPullRequest.body", "github.dialog.newPullRequest.bodyPlaceholder");
    private final BasesField<BranchName> bases;
    private final FormKeys keys;
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public GitHubNewPullRequestDialog(Consumer<Msg> dispatch) {
        this.bases = new BasesField<>(BASES_ID, BranchName::value, dispatch);
        // A blank title keeps the popup open, see GitHubCreatePullRequestUpdate.
        this.keys = new FormKeys(
                () -> dispatch.accept(new PopupMsg.Dismissed(GitHubNewPullRequestPopup.class)),
                () -> dispatch.accept(new GitHubCreatePullRequestMsg.Confirmed()));
        this.dispatch = dispatch;
    }

    /**
     * The ids of the parts of the popup; the title gets the focus when it opens.
     */
    @Override
    public List<String> ids() {
        return List.of(ID, BASES_ID, BODY_ID, DRAFT_ID, STACK_ID);
    }

    /**
     * The popup, while the model asks what a new pull request says.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(GitHubNewPullRequestPopup.class).map(open -> dialog(model, open));
    }

    /**
     * The popup asking what the pull request of {@code open} says. The fields show what the model has: what was typed
     * in them, or what the popup opened with.
     */
    private Element dialog(Model model, GitHubNewPullRequestPopup open) {
        NewPullRequest pullRequest = open.pullRequest();
        boolean draft = pullRequest.draft();
        return new HintedDialog(
                DialogFrame.of(
                        Messages.get(
                                "github.dialog.newPullRequest.title",
                                open.branch().value()),
                        WIDTH,
                        bases.render(
                                open.bases(),
                                open.base(),
                                model.listCursor(),
                                CreatePullRequestMsg.BaseMoved::new,
                                keys::handle),
                        title.render(pullRequest.title(), keys, text -> edited(draft)),
                        body.render(pullRequest.body(), keys, text -> edited(draft)),
                        CheckBox.line(
                                List.<StyledElement<?>>of(
                                                CheckBox.of(DRAFT_ID, draft, () -> edited(!draft), keys),
                                                CheckBox.label(Messages.get("github.dialog.newPullRequest.draft")))
                                        .appendAll(stack(model, open)),
                                List.of(DRAFT_ID, STACK_ID))),
                Messages.get("github.dialog.newPullRequest.hint"));
    }

    /**
     * The box that stacks the pull request on the one of its base, after the draft box, when the base has one
     * (ADR 73): ticked or not; or, when that one is not on the top of its stack, the line off and dimmed, with why.
     */
    private List<StyledElement<?>> stack(Model model, GitHubNewPullRequestPopup open) {
        return GitHubCreatePullRequestUpdate.stackOn(model, open.base())
                .map(on -> on.fold(
                        notTop -> List.<StyledElement<?>>of(text(GAP
                                        + CheckBox.box(false)
                                        + " "
                                        + Messages.get(
                                                "github.dialog.newPullRequest.stack",
                                                String.valueOf(notTop.below().value()))
                                        + " · "
                                        + Messages.get(
                                                "github.dialog.newPullRequest.notTop",
                                                String.valueOf(notTop.below().value()),
                                                String.valueOf(notTop.stack())))
                                .addClass(Theme.Classes.DIM)),
                        stacking -> List.<StyledElement<?>>of(
                                text(GAP),
                                CheckBox.of(
                                        STACK_ID,
                                        open.stack(),
                                        () -> dispatch.accept(new GitHubCreatePullRequestMsg.StackToggled()),
                                        keys),
                                CheckBox.label(Messages.get(
                                        "github.dialog.newPullRequest.stack",
                                        String.valueOf(stacking.below().value()))))))
                .getOrElse(List.empty());
    }

    /**
     * Tells the model what the pull request says now. The text comes from the fields, which hold every key typed so
     * far, also those typed since the model was last rendered.
     */
    private void edited(boolean draft) {
        dispatch.accept(
                new GitHubCreatePullRequestMsg.PullRequestEdited(new NewPullRequest(title.text(), body.text(), draft)));
    }
}
