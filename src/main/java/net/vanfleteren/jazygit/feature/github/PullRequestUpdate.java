package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.LoadPullRequests;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.OpenPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
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
                    popup -> Option.of(new CreatePullRequest(popup.branch(), base)));
            case PullRequestMsg.BaseCancelled() -> Next.of(model.withoutPopup(BasePopup.class));
            case PullRequestMsg.Created(CreatePullRequest cmd, var commands) -> {
                // Done like any operation; then the new pull request is read, to show it.
                Next done = OperationUpdate.update(model, new OperationMsg.Succeeded(cmd, commands));
                yield new Next(done.model(), done.cmds().append(new LoadPullRequests()));
            }
            case PullRequestMsg.Refresh() -> Next.of(model, new LoadPullRequests());
            case PullRequestMsg.Loaded(List<PullRequest> pullRequests) ->
                    Next.of(model.withPullRequests(byHeadBranch(pullRequests)));
            // gh can be missing, logged out or offline, or the remote not on GitHub: then no pull requests show.
            case PullRequestMsg.LoadFailed(var failure) -> Next.of(model);
        };
    }

    /**
     * The pull requests by the branch they merge from. Several can merge from the same branch, into different
     * bases: the newest, the one with the highest number, is kept, so the choice does not depend on the order
     * gh lists them in.
     */
    static Map<String, PullRequest> byHeadBranch(List<PullRequest> pullRequests) {
        return pullRequests.toMap(PullRequest::headBranch, pr -> pr,
                (a, b) -> a.number() >= b.number() ? a : b);
    }

    /**
     * Opens the pull request of the branch. A branch without one gets one, once it is pushed: the user is asked
     * which branch it merges into.
     */
    private static Next requested(Model model, String branch) {
        return model.branch(branch)
                .filter(known -> !model.busy().containsKey(branch))
                .map(known -> model.pullRequest(known)
                        .map(pr -> Update.start(model.clearError(), new OpenPullRequest(branch, pr.number())))
                        .getOrElse(() -> known.upstream().isEmpty() || known.ahead() > 0
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
