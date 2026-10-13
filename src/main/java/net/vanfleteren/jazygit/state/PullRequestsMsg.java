package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequest;

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
    record Refresh() implements PullRequestsMsg {
    }

    record Loaded(List<PullRequest> pullRequests) implements PullRequestsMsg {
    }

    record LoadFailed(Failure failure) implements PullRequestsMsg {
    }
}
