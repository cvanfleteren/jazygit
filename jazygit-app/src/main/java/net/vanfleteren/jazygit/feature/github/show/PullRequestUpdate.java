package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestUpdate;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.PrOverviewUpdate;

/**
 * Showing the pull requests of the branches, and merging, closing, opening and drafting them from there; a
 * branch without one gets one created.
 */
public final class PullRequestUpdate {

    private PullRequestUpdate() {}

    public static Next update(Model model, PullRequestMsg msg) {
        return switch (msg) {
            case PullRequestMsg.Requested(Branch.Name branch) -> requested(model, branch);
            // A pull request of another repository is opened in the browser, as gh acts on this one; so is one
            // while it is not known yet which repository this is, which does no harm.
            case PullRequestMsg.SummaryRequested(var pullRequest)
            when model.prOverview().ofThisRepository(pullRequest).getOrElse(false) ->
                show(model, new Branch.Name(pullRequest.headBranch()), pullRequest.pullRequest());
            case PullRequestMsg.SummaryRequested(var pullRequest) ->
                Update.start(model.clearError(), new BrowsePullRequestCmd(pullRequest));
            // The branches pane shows the pull request as it was just read too, also when the dialog is closed.
            case PullRequestMsg.DetailsLoaded(int number, var details) ->
                Next.of(shown(model.mapGitHub(gitHub -> gitHub.withRead(details)), number, Either.right(details)));
            case PullRequestMsg.DetailsFailed(int number, var failure) ->
                Next.of(shown(model, number, Either.left(failure)));
            // The dialog stays open, so the pull request can still be merged or closed from it.
            case PullRequestMsg.OpenConfirmed() ->
                model.popup(PullRequestPopup.class)
                        .map(popup -> Update.start(
                                model.clearError(),
                                new OpenPullRequest(
                                        popup.branch(), popup.pullRequest().number())))
                        .getOrElse(() -> Next.of(model));
            case PullRequestMsg.MergeChosen(MergeMethod method) ->
                Update.confirm(
                        model,
                        PullRequestPopup.class,
                        popup -> Option.when(
                                popup.mergeable(),
                                () -> new MergePullRequest(
                                        popup.branch(), popup.pullRequest().number(), method)));
            case PullRequestMsg.CloseChosen() ->
                Update.confirm(
                        model,
                        PullRequestPopup.class,
                        popup -> Option.when(
                                popup.open(),
                                () -> new ClosePullRequest(
                                        popup.branch(), popup.pullRequest().number())));
            // The dialog stays open, to show the pull request as it now is.
            case PullRequestMsg.DraftChosen() ->
                model.popup(PullRequestPopup.class)
                        .filter(popup -> draftChangeable(model, popup))
                        .flatMap(popup -> popup.draft()
                                .map(draft -> Update.start(
                                        model.clearError(),
                                        new SetDraft(
                                                popup.branch(),
                                                popup.pullRequest().number(),
                                                !draft))))
                        .getOrElse(() -> Next.of(model));
            // Done like any operation, which reads the pull requests again. The dialog shows the change at once.
            case PullRequestMsg.DraftSet(SetDraft cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                yield PrOverviewUpdate.reloadIfShown(new Next(
                        mapShown(done.model(), cmd.number(), popup -> popup.withDraft(cmd.draft())), done.cmds()));
            }
            case PullRequestMsg.Ended(ChangingCmd cmd, var commands) -> {
                // Done like any operation. The pull request is no longer open, which reading them again confirms;
                // a merge changed the base branch on the remote, which a fetch brings in.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model ended = done.model()
                        .mapGitHub(gitHub -> gitHub.withPullRequests(gitHub.pullRequests()
                                .map(open -> open.filterValues(pr -> pr.number() != cmd.number()))));
                // A read that is still running may have missed it, and would show it open again.
                yield PrOverviewUpdate.reloadIfShown(
                        FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(ended, done.cmds()))));
            }
            case PullRequestMsg.InfoClosed() -> Next.of(model.withoutPopup(PullRequestPopup.class));
        };
    }

    /**
     * Whether the pull request {@code popup} shows can be turned into a draft, or marked ready for review: it is
     * open, and its branch is not busy, e.g. with doing that already.
     */
    static boolean draftChangeable(Model model, PullRequestPopup popup) {
        return popup.open() && !model.jobs().busy().containsKey(new BranchRef.Local(popup.branch()));
    }

    /**
     * {@code model} showing what was read about pull request {@code number}; unchanged when it is no longer
     * shown, as the user closed it, or opened another one, while it was being read.
     */
    private static Model shown(Model model, int number, Either<Failure, PullRequestDetails> read) {
        return mapShown(model, number, popup -> popup.with(read));
    }

    /**
     * {@code model} with the dialog of pull request {@code number} as {@code change} makes it: the one that is open,
     * or the one the open popup goes back to, like a comment on it, so that one is not out of date then. Unchanged
     * when no popup shows it.
     */
    private static Model mapShown(Model model, int number, Function<PullRequestPopup, PullRequestPopup> change) {
        return model.popup(ShowsPullRequest.class)
                .filter(popup -> popup.number() == number)
                .map(popup -> model.openPopup(popup.withPullRequestPopup(change.apply(popup.pullRequestPopup()))))
                .getOrElse(model);
    }

    /**
     * Shows the pull request of the branch, reading what there is to know about it. A branch without one gets one, once it is pushed: the user is asked
     * which branch it merges into.
     */
    private static Next requested(Model model, Branch.Name branch) {
        return model.idleBranch(branch)
                .map(known -> model.gitHub()
                        .pullRequest(known)
                        .map(pr -> show(model, branch, pr))
                        // Without the pull requests it is unknown whether there is one to open.
                        .getOrElse(() -> model.gitHub().pullRequestsFailure().isDefined()
                                        || model.gitHub().pullRequests().isEmpty()
                                ? Next.of(model.fail(model.gitHub()
                                        .pullRequestsFailure()
                                        .<AppError>map(AppError.PullRequestsUnavailable::new)
                                        .getOrElse(AppError.PullRequestsNotRead::new)))
                                : known.remoteUpstream().isEmpty() || known.ahead() > 0
                                        ? Next.of(model.fail(new AppError.NotPushed(branch)))
                                        : CreatePullRequestUpdate.ask(model, known)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Shows {@code pullRequest}, from {@code branch}, reading what there is to know about it.
     */
    private static Next show(Model model, Branch.Name branch, PullRequest pullRequest) {
        return Next.of(
                Update.ask(model, PullRequestPopup.loading(branch, pullRequest)).model(),
                new LoadPullRequestDetails(pullRequest.number()));
    }
}
