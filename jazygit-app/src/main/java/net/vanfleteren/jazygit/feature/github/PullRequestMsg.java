package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.model.Branch;
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
     * The user asked for the pull request of a branch: to show it, or to create it when there is none.
     */
    record Requested(Branch.Name branch) implements PullRequestMsg {}

    /**
     * The details of pull request {@code number} were read.
     */
    record DetailsLoaded(int number, PullRequestDetails details) implements PullRequestMsg {}

    record DetailsFailed(int number, Failure failure) implements PullRequestMsg {}

    /**
     * The user asked to open the pull request that is shown in the browser.
     */
    record OpenConfirmed() implements PullRequestMsg {}

    /**
     * The user asked to merge the pull request that is shown, as {@code method} says.
     */
    record MergeChosen(MergeMethod method) implements PullRequestMsg {}

    /**
     * The user asked to close the pull request that is shown.
     */
    record CloseChosen() implements PullRequestMsg {}

    /**
     * The pull request was merged or closed by running {@code commands}.
     */
    record Ended(PullRequestCmd.ChangingCmd cmd, List<ExecutedCommand> commands) implements PullRequestMsg {}

    record InfoClosed() implements PullRequestMsg {}

    /**
     * The user picked the branch the pull request merges into.
     */
    record BaseChosen(Branch.Name base) implements PullRequestMsg {}

    record BaseCancelled() implements PullRequestMsg {}

    /**
     * The title a new pull request of {@code branch} into {@code base} starts out with was read: the subject of
     * its oldest commit, if one was found.
     *
     * @param head the name the branch has on its remote
     */
    record TitleRead(Branch.Name branch, Branch.Name head, Branch.Name base, Option<String> subject)
            implements PullRequestMsg {}

    /**
     * The user confirmed what the new pull request says.
     */
    record CreateConfirmed(NewPullRequest pullRequest) implements PullRequestMsg {}

    record CreateCancelled() implements PullRequestMsg {}

    /**
     * The pull request was created by running {@code commands}.
     *
     * @param pullRequest the new pull request, as far as it is known before it is read from GitHub
     */
    record Created(
            PullRequestCmd.CreatePullRequest cmd, Option<PullRequest> pullRequest, List<ExecutedCommand> commands)
            implements PullRequestMsg {}
}
