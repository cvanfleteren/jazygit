package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.CheckMerge;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.MergePullRequest;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.PrOverviewUpdate;

/**
 * Merging a pull request, which GitHub does in the background (ADR 24): the merge is kept in the model while GitHub
 * is asked every tick how it is doing, until it is merged, enqueued or failed.
 */
final class MergeUpdate {

    private MergeUpdate() {}

    /**
     * GitHub was asked for the merge of {@code cmd} by running {@code commands}, and said it goes as {@code status}:
     * done like any operation, then kept as the merge of its pull request, and of the ones below it in its stack.
     */
    static Next asked(Model model, MergePullRequest cmd, MergeStatus status, List<ExecutedCommand> commands) {
        Next done = OperationUpdate.succeeded(model, cmd, commands);
        PullRequestMerge merge = PullRequestMerge.asked(
                cmd.number(), cmd.branch(), model.gitHub().below(cmd.number()), status, model.now());
        return went(new Next(done.model().mapGitHub(gitHub -> gitHub.withMerge(merge)), done.cmds()), merge);
    }

    /**
     * Asks GitHub how each pending merge is doing that it is not being asked about already; one that was pending for
     * too long is left to GitHub, and the user told so.
     */
    static Next tick(Model model) {
        List<PullRequestMerge> merges = model.gitHub().merges().values().toList();
        List<PullRequestMerge> givenUp = merges.filter(merge -> merge.givenUp(model.now()));
        List<PullRequestMerge> toCheck =
                merges.filter(PullRequestMerge::toCheck).removeAll(givenUp);
        Model left = givenUp.foldLeft(
                model,
                (m, merge) -> m.mapGitHub(
                                gitHub -> gitHub.withMerges(gitHub.merges().remove(merge.number())))
                        .inform(new Notice.MergeStillPending(merge.branch())));
        Model checking =
                toCheck.foldLeft(left, (m, merge) -> m.mapGitHub(gitHub -> gitHub.withMerge(merge.beingChecked())));
        return new Next(
                checking,
                toCheck.map(merge -> new CheckMerge(merge.number(), ((MergeStatus.Pending) merge.status()).id())));
    }

    /**
     * GitHub said the merge {@code id} of pull request {@code number} is doing as {@code status}. Unchanged when that
     * is no longer the merge that is pending for it.
     */
    static Next checked(Model model, int number, String id, MergeStatus status) {
        return pending(model, number, id)
                .map(merge -> merge.checked(status))
                .map(merge -> went(Next.of(model.mapGitHub(gitHub -> gitHub.withMerge(merge))), merge))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Asking GitHub how the merge {@code id} of pull request {@code number} is doing failed: it is still pending, and
     * the next tick asks again.
     */
    static Next checkFailed(Model model, int number, String id) {
        return Next.of(pending(model, number, id)
                .map(merge -> model.mapGitHub(gitHub -> gitHub.withMerge(merge.checked(merge.status()))))
                .getOrElse(model));
    }

    private static Option<PullRequestMerge> pending(Model model, int number, String id) {
        return model.gitHub()
                .merges()
                .get(number)
                .filter(merge -> merge.status() instanceof MergeStatus.Pending(String pending) && pending.equals(id));
    }

    /**
     * What follows from how {@code merge} now goes: once merged, the pull requests and the remotes are read again,
     * as its base branch changed; a merge that failed is shown as any failed operation.
     */
    private static Next went(Next next, PullRequestMerge merge) {
        Model model = next.model();
        return switch (merge.status()) {
            case MergeStatus.Pending pending -> next;
            // A read that is still running may have missed it, and would show it open again.
            case MergeStatus.Merged merged ->
                PrOverviewUpdate.reloadIfShown(FetchUpdate.requested(PullRequestsUpdate.loadAnyway(next)));
            case MergeStatus.Enqueued enqueued ->
                new Next(model.inform(new Notice.MergeEnqueued(merge.branch())), next.cmds());
            case MergeStatus.Failed(String message) ->
                new Next(model.fail(new AppError.MergeFailed(merge.branch(), message)), next.cmds());
        };
    }
}
