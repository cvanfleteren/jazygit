package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.ChangingCmd;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.ClosePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.MergePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.LoadPullRequestDetails;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.OpenPullRequest;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.PullRequestsUpdate;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Creating, opening and showing the pull requests of the branches.
 */
public final class PullRequestUpdate {

    private PullRequestUpdate() {
    }

    public static Next update(Model model, PullRequestMsg msg) {
        return switch (msg) {
            case PullRequestMsg.Requested(String branch) -> requested(model, branch);
            case PullRequestMsg.BaseChosen(String base) -> Update.confirm(model, BasePopup.class,
                    // The branch can have another name on its remote; gh needs that one.
                    popup -> model.branch(popup.branch()).map(known ->
                            new CreatePullRequest(known.name(), known.pushTarget().branch(), base)));
            case PullRequestMsg.BaseCancelled() -> Next.of(model.withoutPopup(BasePopup.class));
            case PullRequestMsg.Created(CreatePullRequest cmd, var pullRequest, var commands) -> {
                // Done like any operation. The new pull request shows at once, as GitHub may not list it yet;
                // reading the pull requests then gives its checks and review.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model shown = pullRequest
                        .map(pr -> done.model().withPullRequests(done.model().pullRequests().map(open -> open.put(pr.headBranch(), pr))))
                        .getOrElse(done.model());
                // A read that is still running may have missed it, so this one is started anyway. GitHub may have
                // changed the remote too, which a fetch brings in.
                yield FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(shown, done.cmds())));
            }
            case PullRequestMsg.DetailsLoaded(int number, var details) -> Next.of(shown(model, number, Either.right(details)));
            case PullRequestMsg.DetailsFailed(int number, var failure) -> Next.of(shown(model, number, Either.left(failure)));
            // The dialog stays open, so the pull request can still be merged or closed from it.
            case PullRequestMsg.OpenConfirmed() -> model.popup(PullRequestPopup.class)
                    .map(popup -> Update.start(model.clearError(),
                            new OpenPullRequest(popup.branch(), popup.pullRequest().number())))
                    .getOrElse(() -> Next.of(model));
            case PullRequestMsg.MergeChosen(MergeMethod method) -> Update.confirm(model, PullRequestPopup.class,
                    popup -> Option.when(popup.mergeable(),
                            () -> new MergePullRequest(popup.branch(), popup.pullRequest().number(), method)));
            case PullRequestMsg.CloseChosen() -> Update.confirm(model, PullRequestPopup.class,
                    popup -> Option.when(popup.open(),
                            () -> new ClosePullRequest(popup.branch(), popup.pullRequest().number())));
            case PullRequestMsg.Ended(ChangingCmd cmd, var commands) -> {
                // Done like any operation. The pull request is no longer open, which reading them again confirms;
                // a merge changed the base branch on the remote, which a fetch brings in.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model ended = done.model().withPullRequests(done.model().pullRequests()
                        .map(open -> open.filterValues(pr -> pr.number() != cmd.number())));
                // A read that is still running may have missed it, and would show it open again.
                yield FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(ended, done.cmds())));
            }
            case PullRequestMsg.InfoClosed() -> Next.of(model.withoutPopup(PullRequestPopup.class));
        };
    }

    /**
     * {@code model} showing what was read about pull request {@code number}; unchanged when it is no longer
     * shown, as the user closed it, or opened another one, while it was being read.
     */
    private static Model shown(Model model, int number, Either<Failure, PullRequestDetails> read) {
        return model.popup(PullRequestPopup.class)
                .filter(popup -> popup.pullRequest().number() == number)
                .map(popup -> model.openPopup(popup.with(read)))
                .getOrElse(model);
    }

    /**
     * Shows the pull request of the branch, reading what there is to know about it. A branch without one gets one, once it is pushed: the user is asked
     * which branch it merges into.
     */
    private static Next requested(Model model, String branch) {
        return model.idleBranch(branch)
                .map(known -> model.pullRequest(known)
                        .map(pr -> Next.of(Update.ask(model, PullRequestPopup.loading(branch, pr)).model(),
                                new LoadPullRequestDetails(pr.number())))
                        // Without the pull requests it is unknown whether there is one to open.
                        .getOrElse(() -> model.pullRequestsFailure().isDefined() || model.pullRequests().isEmpty()
                                ? Next.of(model.fail(model.pullRequestsFailure()
                                        .<AppError>map(AppError.PullRequestsUnavailable::new)
                                        .getOrElse(AppError.PullRequestsNotRead::new)))
                                : known.remoteUpstream().isEmpty() || known.ahead() > 0
                                ? Next.of(model.fail(new AppError.NotPushed(branch)))
                                : Update.ask(model, new BasePopup(branch, bases(model, branch)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The branches a pull request from {@code branch} can merge into: the others that are on their remote, the
     * default branch first.
     */
    static List<String> bases(Model model, String branch) {
        return model.branches().toOption().getOrElse(List.empty())
                .filter(b -> b.hasRemote() && !b.name().equals(branch))
                .sortBy(b -> !b.isDefault())
                .map(Branch::name);
    }
}
