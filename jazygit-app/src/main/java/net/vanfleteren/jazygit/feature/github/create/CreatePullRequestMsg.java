package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Creating the pull request of a branch that has none: choosing what it merges into, then what it says.
 */
public sealed interface CreatePullRequestMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CreatePullRequestUpdate.update(model, this);
    }

    /**
     * The user picked the branch the pull request merges into.
     */
    record BaseChosen(Branch.Name base) implements CreatePullRequestMsg {}

    record BaseCancelled() implements CreatePullRequestMsg {}

    /**
     * The title a new pull request of {@code branch} into {@code base} starts out with was read: the subject of
     * its oldest commit, if one was found.
     *
     * @param head the name the branch has on its remote
     */
    record TitleRead(Branch.Name branch, Branch.Name head, Branch.Name base, Option<String> subject)
            implements CreatePullRequestMsg {}

    /**
     * The user changed what the new pull request says.
     */
    record PullRequestEdited(NewPullRequest pullRequest) implements CreatePullRequestMsg {}

    /**
     * The user confirmed what the new pull request says.
     */
    record Confirmed() implements CreatePullRequestMsg {}

    record Cancelled() implements CreatePullRequestMsg {}

    /**
     * The pull request was created by running {@code commands}.
     *
     * @param pullRequest the new pull request, as far as it is known before it is read from GitHub
     */
    record Created(
            CreatePullRequestCmd.CreatePullRequest cmd, Option<PullRequest> pullRequest, List<ExecutedCommand> commands)
            implements CreatePullRequestMsg {}
}
