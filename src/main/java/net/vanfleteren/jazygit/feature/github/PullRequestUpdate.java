package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.ChangingCmd;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.ClosePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.MergePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.LoadPullRequestDetails;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.LoadPullRequests;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.OpenPullRequest;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationMsg;
import net.vanfleteren.jazygit.state.OperationUpdate;
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
                Next done = OperationUpdate.update(model, new OperationMsg.Succeeded(cmd, commands));
                Model shown = pullRequest
                        .map(pr -> done.model().withPullRequests(done.model().pullRequests().put(pr.headBranch(), pr)))
                        .getOrElse(done.model());
                // A read that is still running may have missed it, so this one is started anyway.
                yield new Next(reading(shown), done.cmds().append(new LoadPullRequests()));
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
                // Done like any operation. The pull request is no longer open, which reading them again confirms.
                Next done = OperationUpdate.update(model, new OperationMsg.Succeeded(cmd, commands));
                Model ended = done.model().withPullRequests(
                        done.model().pullRequests().filterValues(pr -> pr.number() != cmd.number()));
                yield load(new Next(ended, done.cmds()));
            }
            case PullRequestMsg.InfoClosed() -> Next.of(model.withoutPopup(PullRequestPopup.class));
            case PullRequestMsg.Refresh() -> load(Next.of(model));
            case PullRequestMsg.Loaded(List<PullRequest> pullRequests) -> Next.of(read(model)
                    .withPullRequests(byHeadBranch(pullRequests)).withPullRequestsFailure(Option.none()));
            // gh can be missing, logged out or offline, or the remote not on GitHub: then no pull requests show.
            // That is no reason to bother the user until they ask for a pull request, see requested().
            case PullRequestMsg.LoadFailed(var failure) -> Next.of(read(model).withPullRequestsFailure(Option.of(failure)));
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
     * {@code next}, also reading the pull requests again; unless a read is still running, which gets the
     * latest state already.
     */
    public static Next load(Next next) {
        return next.model().readingPullRequests() > 0
                ? next
                : new Next(reading(next.model()), next.cmds().append(new LoadPullRequests()));
    }

    private static Model reading(Model model) {
        return model.withReadingPullRequests(model.readingPullRequests() + 1);
    }

    private static Model read(Model model) {
        return model.withReadingPullRequests(Math.max(0, model.readingPullRequests() - 1));
    }

    /**
     * The pull requests by the branch they merge from. Several can merge from the same branch, into different
     * bases: the newest, the one with the highest number, is kept, so the choice does not depend on the order
     * gh lists them in.
     */
    static Map<String, PullRequest> byHeadBranch(List<PullRequest> pullRequests) {
        // Vavr's toMap has no merge function; a later entry replaces an earlier one, so ascending numbers keep the newest.
        return pullRequests.sortBy(PullRequest::number).toMap(PullRequest::headBranch, pr -> pr);
    }

    /**
     * Shows the pull request of the branch, reading what there is to know about it. A branch without one gets one, once it is pushed: the user is asked
     * which branch it merges into.
     */
    private static Next requested(Model model, String branch) {
        return model.branch(branch)
                .filter(known -> !model.busy().containsKey(branch))
                .map(known -> model.pullRequest(known)
                        .map(pr -> Next.of(model.openPopup(PullRequestPopup.loading(branch, pr)).clearError(),
                                new LoadPullRequestDetails(pr.number())))
                        .getOrElse(() -> model.pullRequestsFailure().isDefined()
                                // Without the pull requests it is unknown whether there is one to open.
                                ? Next.of(model.fail(new AppError.PullRequestsUnavailable(model.pullRequestsFailure().get())))
                                : known.upstream().isEmpty() || known.ahead() > 0
                                ? Next.of(model.fail(new AppError.NotPushed(branch)))
                                : Next.of(model.openPopup(new BasePopup(branch, bases(model, branch))).clearError())))
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
