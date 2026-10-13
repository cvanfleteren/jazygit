package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface PullRequestMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PullRequestUpdate.update(model, this);
    }

    /**
     * The user asked for the pull request of a branch: to open it, or to create it when there is none.
     */
    record Requested(String branch) implements PullRequestMsg {
    }

    /**
     * The user picked the branch the pull request merges into.
     */
    record BaseChosen(String base) implements PullRequestMsg {
    }

    record BaseCancelled() implements PullRequestMsg {
    }

    /**
     * The pull request was created by running {@code commands}.
     */
    record Created(PullRequestCmd.CreatePullRequest cmd, List<ExecutedCommand> commands) implements PullRequestMsg {
    }

    /**
     * Read the pull requests again.
     */
    record Refresh() implements PullRequestMsg {
    }

    record Loaded(List<PullRequest> pullRequests) implements PullRequestMsg {
    }

    record LoadFailed(Failure failure) implements PullRequestMsg {
    }
}
