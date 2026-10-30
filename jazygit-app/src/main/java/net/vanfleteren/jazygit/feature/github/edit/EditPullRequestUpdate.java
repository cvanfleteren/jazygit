package net.vanfleteren.jazygit.feature.github.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.github.OnGitHub;
import net.vanfleteren.jazygit.feature.github.edit.EditPullRequestCmd.EditBody;
import net.vanfleteren.jazygit.feature.github.edit.EditPullRequestCmd.SavePullRequest;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestUpdate;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestEdit;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.PrOverviewUpdate;

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
            // Once the details are read, whatever state the pull request is in: closed and merged ones are edited too.
            case EditPullRequestMsg.Requested() ->
                Update.ask(
                        model,
                        model.popup(PullRequestPopup.class)
                                .flatMap(shown -> shown.readDetails().map(details -> opened(model, shown, details))));
            // A move that shows no base, as the filter hides them all, keeps the base.
            case EditPullRequestMsg.BaseMoved(var cursor, var base) ->
                Next.of(editable(model)
                        .filter(EditPullRequestPopup::baseChangeable)
                        .map(popup -> model.openPopup(popup.withFields(
                                        base.map(popup.fields()::withBase).getOrElse(popup.fields())))
                                .withListCursor(cursor))
                        .getOrElse(model));
            case EditPullRequestMsg.Edited(String title, String body, boolean draft) ->
                Next.of(editable(model)
                        .map(popup -> model.openPopup(popup.withFields(popup.fields()
                                .withTitle(title)
                                .withBody(body)
                                .withDraft(
                                        popup.draftChangeable()
                                                ? draft
                                                : popup.fields().draft()))))
                        .getOrElse(model));
            // A blank title is not saved, as GitHub needs one; with nothing changed, there is nothing to save.
            case EditPullRequestMsg.Confirmed() ->
                editable(model)
                        .filter(popup -> !popup.fields().title().isBlank())
                        .map(popup -> save(popup)
                                .map(cmd -> Update.start(model.clearError().openPopup(popup.withSaving(true)), cmd))
                                .getOrElse(() -> Next.of(model.clearError().openPopup(popup.from()))))
                        .getOrElse(() -> Next.of(model));
            case EditPullRequestMsg.Cancelled() ->
                Next.of(editable(model)
                        .map(popup -> model.clearError().openPopup(popup.from()))
                        .getOrElse(model));
            case EditPullRequestMsg.EditorRequested() ->
                editable(model)
                        .map(popup -> Next.of(
                                model.clearError(), new EditBody(popup.fields().body())))
                        .getOrElse(() -> Next.of(model));
            // Cancelled meanwhile, the popup stays closed.
            case EditPullRequestMsg.EditorClosed(String text, var commands) ->
                Next.of(changed(
                        CommandLog.append(model, EditPullRequestCmd.EDIT_BODY, commands),
                        popup -> popup.withFields(popup.fields().withBody(text))));
            case EditPullRequestMsg.EditorFailed(Failure failure) ->
                Next.of(CommandLog.append(model, EditPullRequestCmd.EDIT_BODY, failure.commands())
                        .fail(new AppError.OperationFailed(EditPullRequestCmd.EDIT_BODY, Option.none(), failure)));
            // A read of the pull request that started before it was saved is not taken; the one started now is the
            // dialog's own, read quietly (ADR 34).
            case EditPullRequestMsg.Saved(SavePullRequest cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model changed = done.model().mapGitHub(gitHub -> gitHub.withChanged(cmd.number()));
                Next back = changed.popup(EditPullRequestPopup.class)
                        .filter(popup -> popup.number() == cmd.number())
                        .map(popup -> readAgain(changed, popup.from(), done.cmds()))
                        .getOrElse(() -> new Next(changed, done.cmds()));
                yield cmd.changesGitHub() ? PrOverviewUpdate.reloadIfShown(back) : back;
            }
            // The popup stays open, with what was typed, to save it again. When only making it a draft or ready failed,
            // the rest was saved, which the popup then counts as opened with, and the dialog under it reads again.
            case EditPullRequestMsg.SaveFailed(SavePullRequest cmd, Failure failure, boolean edited) -> {
                Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
                Model reopened = changed(
                        failed.model(),
                        popup -> popup.withSaving(false).withOpened(edited ? saved(popup) : popup.opened()));
                yield edited
                        ? readUnder(
                                reopened.mapGitHub(gitHub -> gitHub.withChanged(cmd.number())),
                                cmd.number(),
                                failed.cmds())
                        : new Next(reopened, failed.cmds());
            }
        };
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
                .filter(remote ->
                        !remote.branch().value().equals(details.branches().head()))
                .distinctBy(RemoteBranch::branch);
        RemoteBranch current = others.find(remote ->
                        remote.branch().value().equals(details.branches().base()))
                .getOrElse(() -> new RemoteBranch(
                        RemoteBranch.DEFAULT_REMOTE,
                        new BranchName(details.branches().base())));
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
    private static PullRequestFields saved(EditPullRequestPopup popup) {
        PullRequestFields fields = popup.fields();
        return popup.opened()
                .withTitle(fields.title().strip())
                .withBody(fields.body())
                .withBase(fields.base());
    }

    /**
     * {@code model} back on the pull request dialog {@code from}, which reads its details again, quietly, after
     * {@code cmds}.
     */
    private static Next readAgain(Model model, PullRequestPopup from, List<Cmd> cmds) {
        return PullRequestUpdate.read(model, from.number())
                .apply((given, load) -> new Next(
                        given.openPopup(from.readingAgain(Reading.BY_ITSELF, model.now(), load.read())),
                        cmds.append(load)));
    }

    /**
     * {@code model} with the pull request dialog under the edit popup of pull request {@code number} reading its
     * details again, quietly, after {@code cmds}, if that popup is open.
     */
    private static Next readUnder(Model model, int number, List<Cmd> cmds) {
        return model.popup(EditPullRequestPopup.class)
                .filter(popup -> popup.number() == number)
                .map(popup -> PullRequestUpdate.read(model, number)
                        .apply((given, load) -> new Next(
                                given.openPopup(popup.withFrom(
                                        popup.from().readingAgain(Reading.BY_ITSELF, model.now(), load.read()))),
                                cmds.append(load))))
                .getOrElse(() -> new Next(model, cmds));
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
