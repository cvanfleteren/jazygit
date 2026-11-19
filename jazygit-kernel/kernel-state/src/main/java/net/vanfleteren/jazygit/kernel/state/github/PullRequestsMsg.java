package net.vanfleteren.jazygit.kernel.state.github;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Reading the open pull requests from GitHub.
 */
public sealed interface PullRequestsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PullRequestsUpdate.update(model, this);
    }

    /**
     * Read the pull requests again.
     */
    record Refresh() implements PullRequestsMsg {}

    record Loaded(List<PullRequest> pullRequests) implements PullRequestsMsg {}

    record LoadFailed(Failure failure) implements PullRequestsMsg {}

    /**
     * The branches that are protected on GitHub (ADR 72).
     */
    record ProtectedLoaded(List<GhBranch> branches) implements PullRequestsMsg {}

    record ProtectedLoadFailed(Failure failure) implements PullRequestsMsg {}
}
