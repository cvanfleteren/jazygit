package net.vanfleteren.jazygit.features.forgejo.create;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.BasesField;
import net.vanfleteren.jazygit.kernel.ui.widgets.BodyField;
import net.vanfleteren.jazygit.kernel.ui.widgets.CheckBox;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.FormKeys;
import net.vanfleteren.jazygit.kernel.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.TitleField;

/**
 * Popup asking what a new pull request on Forgejo merges into and what it says (ADR 178), laid out as GitHub's (ADR
 * 18, 175): the list of the branches of the home's remote it can merge into, its title, which starts out as the
 * subject of the oldest commit of the branch not on the base, its body, which starts out empty, and whether it is a
 * draft. Tab goes from one part to the next; the focus starts in the title. In the list, the highlighted branch is the
 * base; the arrows move it, typing filters the list, and Enter goes on to the title. Space or Enter on the draft box
 * ticks it. Enter in the title, or Ctrl+S anywhere, creates the pull request; Escape clears the filter, or else
 * cancels. What is picked, typed and ticked lives in the {@link Model}, which every change is sent to; the fields only
 * keep where their cursor is.
 */
public final class ForgejoNewPullRequestDialog implements PopupView {

    /**
     * The id of the title field, which gets the focus when the popup opens.
     */
    public static final String ID = "forgejo-new-pull-request-title";

    public static final String BASES_ID = "forgejo-new-pull-request-bases";
    public static final String BODY_ID = "forgejo-new-pull-request-body";
    public static final String DRAFT_ID = "forgejo-new-pull-request-draft";

    private static final int WIDTH = 110;

    private final TitleField title = new TitleField(
            ID, "forgejo.dialog.newPullRequest.titleField", "forgejo.dialog.newPullRequest.titlePlaceholder");
    private final BodyField body = new BodyField(
            BODY_ID, "forgejo.dialog.newPullRequest.body", "forgejo.dialog.newPullRequest.bodyPlaceholder");
    private final BasesField<BranchName> bases;
    private final FormKeys keys;
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ForgejoNewPullRequestDialog(Consumer<Msg> dispatch) {
        this.bases = new BasesField<>(BASES_ID, BranchName::value, dispatch);
        // A blank title keeps the popup open, see ForgejoCreatePullRequestUpdate.
        this.keys = new FormKeys(
                () -> dispatch.accept(new PopupMsg.Dismissed(ForgejoNewPullRequestPopup.class)),
                () -> dispatch.accept(new ForgejoCreatePullRequestMsg.Confirmed()));
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
     * The popup, while the model asks what a new pull request says.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(ForgejoNewPullRequestPopup.class).map(open -> dialog(model, open));
    }

    /**
     * The popup asking what the pull request of {@code open} says. The fields show what the model has: what was typed
     * in them, or what the popup opened with.
     */
    private Element dialog(Model model, ForgejoNewPullRequestPopup open) {
        boolean draft = open.draft();
        return new HintedDialog(
                DialogFrame.of(
                        Messages.get(
                                "forgejo.dialog.newPullRequest.title",
                                open.branch().value()),
                        WIDTH,
                        bases.render(
                                open.bases(),
                                open.base(),
                                model.listCursor(),
                                CreatePullRequestMsg.BaseMoved::new,
                                keys::handle),
                        title.render(open.title(), keys, text -> edited(draft)),
                        body.render(open.body(), keys, text -> edited(draft)),
                        CheckBox.line(
                                List.<StyledElement<?>>of(
                                        CheckBox.of(DRAFT_ID, draft, () -> edited(!draft), keys),
                                        CheckBox.label(Messages.get("forgejo.dialog.newPullRequest.draft"))),
                                List.of(DRAFT_ID))),
                Messages.get("forgejo.dialog.newPullRequest.hint"));
    }

    /**
     * Tells the model what the pull request says now. The text comes from the fields, which hold every key typed so
     * far, also those typed since the model was last rendered.
     */
    private void edited(boolean draft) {
        dispatch.accept(new ForgejoCreatePullRequestMsg.Edited(title.text(), body.text(), draft));
    }
}
