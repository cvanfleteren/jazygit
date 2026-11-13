package net.vanfleteren.jazygit.feature.github.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.github.OnGitHub;
import net.vanfleteren.jazygit.feature.github.edit.EditPullRequestCmd.EditBody;
import net.vanfleteren.jazygit.feature.github.edit.EditPullRequestCmd.SavePullRequest;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestUpdate;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestEdit;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewUpdate;
import net.vanfleteren.jazygit.state.operation.*;
import net.vanfleteren.jazygit.state.popup.ListCursor;

/**
 * Editing the pull request that is shown (ADR 59): its title, body, base and whether it is a draft, in a popup of its
 * own, which starts out from the details read last and stays open until what was changed is saved, so an edit that
 * fails to save is not lost. Only what was changed is saved; once it is, the pull request dialog comes back and reads
 * its details again.
 */
public final class EditPullRequestUpdate {

    private EditPullRequestUpdate() {}

    public static Next update(Model model, EditPullRequestMsg msg) {
        return switch (msg) {
            case EditPullRequestMsg.Requested() -> requested(model);
            case EditPullRequestMsg.BaseMoved(var cursor, var base) -> baseMoved(model, cursor, base);
            case EditPullRequestMsg.Edited(String title, String body, boolean draft) ->
                edited(model, title, body, draft);
            case EditPullRequestMsg.Confirmed() -> confirmed(model);
            case EditPullRequestMsg.Cancelled() -> cancelled(model);
            case EditPullRequestMsg.EditorRequested() -> editorRequested(model);
            case EditPullRequestMsg.EditorClosed(String text, var commands) -> editorClosed(model, text, commands);
            case EditPullRequestMsg.EditorFailed(Failure failure) -> editorFailed(model, failure);
            case EditPullRequestMsg.Saved(SavePullRequest cmd, var commands) -> saved(model, cmd, commands);
            case EditPullRequestMsg.SaveFailed(SavePullRequest cmd, Failure failure, boolean edited) ->
                saveFailed(model, cmd, failure, edited);
        };
    }

    /**
     * Opens the edit popup once the details are read, whatever state the pull request is in: closed and merged ones
     * are edited too.
     */
    private static Next requested(Model model) {
        return Update.ask(
                model,
                model.popup(PullRequestPopup.class)
                        .flatMap(shown -> shown.readDetails().map(details -> opened(model, shown, details))));
    }

    /**
     * Moves in the list of bases to {@code cursor}. A move that shows no base, as the filter hides them all, keeps
     * the base.
     */
    private static Next baseMoved(Model model, ListCursor cursor, Option<RemoteBranch> base) {
        return Next.of(editable(model)
                .filter(EditPullRequestPopup::baseChangeable)
                .map(popup -> model.openPopup(popup.withFields(
                                base.map(popup.fields()::withBase).getOrElse(popup.fields())))
                        .withListCursor(cursor))
                .getOrElse(model));
    }

    /**
     * The edit popup with the title, body and, when it can be changed, whether it is a draft as the user typed them.
     */
    private static Next edited(Model model, String title, String body, boolean draft) {
        return Next.of(editable(model)
                .map(popup -> model.openPopup(popup.withFields(popup.fields()
                        .withTitle(title)
                        .withBody(body)
                        .withDraft(
                                popup.draftChangeable() ? draft : popup.fields().draft()))))
                .getOrElse(model));
    }

    /**
     * Saves what was changed. A blank title is not saved, as GitHub needs one; with nothing changed, there is
     * nothing to save.
     */
    private static Next confirmed(Model model) {
        return editable(model)
                .filter(popup -> !popup.fields().title().isBlank())
                .map(popup -> save(popup)
                        .map(cmd -> Update.start(model.clearError().openPopup(popup.withSaving(true)), cmd))
                        .getOrElse(() -> Next.of(model.clearError().openPopup(popup.from()))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Back on the pull request dialog the edit popup came from.
     */
    private static Next cancelled(Model model) {
        return Next.of(editable(model)
                .map(popup -> model.clearError().openPopup(popup.from()))
                .getOrElse(model));
    }

    /**
     * Edits the body in the external editor.
     */
    private static Next editorRequested(Model model) {
        return editable(model)
                .map(popup ->
                        Next.of(model.clearError(), new EditBody(popup.fields().body())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The edit popup with the body as the editor left it. Cancelled meanwhile, the popup stays closed.
     */
    private static Next editorClosed(Model model, String text, List<ExecutedCommand> commands) {
        return Next.of(changed(
                CommandLog.append(model, EditPullRequestCmd.EDIT_BODY, commands),
                popup -> popup.withFields(popup.fields().withBody(text))));
    }

    /**
     * Shows why the editor failed.
     */
    private static Next editorFailed(Model model, Failure failure) {
        return Next.of(CommandLog.append(model, EditPullRequestCmd.EDIT_BODY, failure.commands())
                .fail(new AppError.OperationFailed(EditPullRequestCmd.EDIT_BODY, Option.none(), failure)));
    }

    /**
     * Back on the pull request dialog once saved. A read of the pull request that started before it was saved is not
     * taken; the one started now is the dialog's own, read quietly (ADR 34).
     */
    private static Next saved(Model model, SavePullRequest cmd, List<ExecutedCommand> commands) {
        Next back = OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> done.mapGitHub(gitHub -> gitHub.withChanged(cmd.number())))
                .then(changed -> changed.popup(EditPullRequestPopup.class)
                        .filter(popup -> popup.number().equals(cmd.number()))
                        .map(popup -> readAgain(changed, popup.from()))
                        .getOrElse(() -> Next.of(changed)));
        return cmd.changesGitHub() ? PrOverviewUpdate.reloadIfShown(back) : back;
    }

    /**
     * The popup stays open, with what was typed, to save it again. When only making it a draft or ready failed, the
     * rest was saved, which the popup then counts as opened with, and the dialog under it reads again; a new base
     * shows on the branch, so the pull requests are read again too.
     */
    private static Next saveFailed(Model model, SavePullRequest cmd, Failure failure, boolean edited) {
        Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(reopened -> changed(
                        reopened,
                        popup -> popup.withSaving(false).withOpened(edited ? savedFields(popup) : popup.opened())));
        Next read = edited
                ? failed.map(reopened -> reopened.mapGitHub(gitHub -> gitHub.withChanged(cmd.number())))
                        .then(changed -> readUnder(changed, cmd.number()))
                : failed;
        return edited && cmd.edit().base().isDefined()
                ? PrOverviewUpdate.reloadIfShown(PullRequestsUpdate.loadAnyway(read))
                : read;
    }

    /**
     * The popup to edit the pull request {@code shown} shows, as {@code details} have it: its base can be changed when
     * it is open and not stacked on another, and whether it is a draft when it is open.
     */
    static EditPullRequestPopup opened(Model model, PullRequestPopup shown, PullRequestDetails details) {
        boolean open = details.status().state() == PullRequestDetails.State.OPEN;
        List<RemoteBranch> bases = bases(model, shown.branch(), details);
        PullRequestFields fields = new PullRequestFields(
                details.title(), details.body(), bases.head(), details.status().draft());
        return new EditPullRequestPopup(
                shown,
                bases,
                fields,
                fields,
                open && details.stack().forAll(stack -> stack.position() == 1),
                open,
                false);
    }

    /**
     * The branches on the remote a pull request from {@code branch}, as {@code details} have it, can merge into: the
     * one it merges into now first, also when it is not on the remote, then the others the popup to create one
     * offers, the default branch first. Not the one it merges from.
     */
    static List<RemoteBranch> bases(Model model, BranchName branch, PullRequestDetails details) {
        List<RemoteBranch> others = model.repo()
                .branches()
                .toOption()
                .getOrElse(List.empty())
                .filter(b -> b.hasRemote() && !b.name().equals(branch))
                .sortBy(b -> !b.isDefault())
                .flatMap(Branch::remoteUpstream)
                .filter(remote -> !remote.branch()
                        .equals(FromGitHub.branch(details.branches().head())))
                .distinctBy(RemoteBranch::branch);
        RemoteBranch current = others.find(remote -> remote.branch()
                        .equals(FromGitHub.branch(details.branches().base())))
                .getOrElse(() -> new RemoteBranch(
                        RemoteBranch.DEFAULT_REMOTE,
                        FromGitHub.branch(details.branches().base())));
        return others.remove(current).prepend(current);
    }

    /**
     * Saving what was changed in {@code popup}, if anything was: the title without the whitespace around it.
     */
    static Option<SavePullRequest> save(EditPullRequestPopup popup) {
        PullRequestFields opened = popup.opened();
        PullRequestFields fields = popup.fields();
        String title = fields.title().strip();
        PullRequestEdit edit = new PullRequestEdit(
                Option.when(!title.equals(opened.title()), title),
                Option.when(!fields.body().equals(opened.body()), fields.body()),
                Option.when(!fields.sameBase(opened), () -> OnGitHub.branch(fields.base())));
        Option<Boolean> draft = Option.when(fields.draft() != opened.draft(), fields.draft());
        return Option.when(
                edit.changes() || draft.isDefined(),
                () -> new SavePullRequest(popup.pullRequestPopup().branch(), popup.number(), edit, draft));
    }

    /**
     * What the popup counts as opened with once the title, body and base it shows were saved, but not whether it is
     * a draft.
     */
    private static PullRequestFields savedFields(EditPullRequestPopup popup) {
        PullRequestFields fields = popup.fields();
        return popup.opened()
                .withTitle(fields.title().strip())
                .withBody(fields.body())
                .withBase(fields.base());
    }

    /**
     * {@code model} back on the pull request dialog {@code from}, which reads its details again, quietly.
     */
    private static Next readAgain(Model model, PullRequestPopup from) {
        return PullRequestUpdate.read(model, from.number())
                .apply((given, load) ->
                        Next.of(given.openPopup(from.readingAgain(Reading.BY_ITSELF, model.now(), load.read())), load));
    }

    /**
     * {@code model} with the pull request dialog under the edit popup of pull request {@code number} reading its
     * details again, quietly, if that popup is open.
     */
    private static Next readUnder(Model model, PrNumber number) {
        return model.popup(EditPullRequestPopup.class)
                .filter(popup -> popup.number().equals(number))
                .map(popup -> PullRequestUpdate.read(model, number)
                        .apply((given, load) -> Next.of(
                                given.openPopup(popup.withFrom(
                                        popup.from().readingAgain(Reading.BY_ITSELF, model.now(), load.read()))),
                                load)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The edit popup, if it is open and not being saved.
     */
    private static Option<EditPullRequestPopup> editable(Model model) {
        return model.popup(EditPullRequestPopup.class).filter(popup -> !popup.saving());
    }

    /**
     * {@code model} with the edit popup as {@code change} makes it, if it is open.
     */
    private static Model changed(Model model, Function<EditPullRequestPopup, EditPullRequestPopup> change) {
        return model.popup(EditPullRequestPopup.class)
                .map(popup -> model.openPopup(change.apply(popup)))
                .getOrElse(model);
    }
}
