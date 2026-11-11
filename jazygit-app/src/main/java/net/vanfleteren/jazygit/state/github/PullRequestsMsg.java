package net.vanfleteren.jazygit.state.github;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
    record ProtectedLoaded(List<String> branches) implements PullRequestsMsg {}

    record ProtectedLoadFailed(Failure failure) implements PullRequestsMsg {}
}
