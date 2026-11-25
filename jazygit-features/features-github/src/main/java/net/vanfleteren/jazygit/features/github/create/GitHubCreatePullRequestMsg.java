package net.vanfleteren.jazygit.features.github.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Creating the pull request of a branch that has none on GitHub: reading the branch it came from, then asking what it
 * merges into and what it says, in one popup. Pushing it first and the title that follows the base are
 * {@code CreatePullRequestMsg}'s.
 */
public sealed interface GitHubCreatePullRequestMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return GitHubCreatePullRequestUpdate.update(model, this);
    }

    /**
     * The branch {@code branch} was made from was read, if it could be told: the base that is offered first.
     */
    record ParentRead(BranchName branch, Option<BranchName> parent) implements GitHubCreatePullRequestMsg {}

    /**
     * The user changed what the new pull request says.
     */
    record PullRequestEdited(NewPullRequest pullRequest) implements GitHubCreatePullRequestMsg {}

    /**
     * The user ticked or unticked stacking the new pull request on the one of its base (ADR 73).
     */
    record StackToggled() implements GitHubCreatePullRequestMsg {}

    /**
     * The user confirmed creating the pull request of a branch to put it on a stack, as the stack menu asked
     * (ADR 148).
     */
    record StackRequested() implements GitHubCreatePullRequestMsg {}

    /**
     * The branch a pull request is created for to put it on a stack was recorded as built on its base, by running
     * {@code commands}: asking what the pull request says comes next (ADR 148).
     */
    record ParentRecorded(GitHubCreatePullRequestCmd.RecordParent cmd, List<ExecutedCommand> commands)
            implements GitHubCreatePullRequestMsg {}

    /**
     * The user confirmed what the new pull request says.
     */
    record Confirmed() implements GitHubCreatePullRequestMsg {}

    /**
     * The pull request was created by running {@code commands}.
     *
     * @param pullRequest the new pull request, as far as it is known before it is read from GitHub
     */
    record Created(
            GitHubCreatePullRequestCmd.CreatePullRequest cmd,
            Option<PullRequest> pullRequest,
            List<ExecutedCommand> commands)
            implements GitHubCreatePullRequestMsg {}
}
