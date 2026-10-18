package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Showing the pull request of a branch, and what is done to it from there: opening it in the browser, merging,
 * closing, and turning it into a draft or back.
 */
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
     * The user asked for a pull request listed on the pull requests screen: to show it when it is of the
     * repository, else to open it in the browser.
     */
    record SummaryRequested(PullRequestSummary pullRequest) implements PullRequestMsg {}

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
     * The user asked to turn the pull request that is shown into a draft, or to mark it ready for review.
     */
    record DraftChosen() implements PullRequestMsg {}

    /**
     * The pull request became a draft, or ready for review, as {@code cmd} says, by running {@code commands}.
     */
    record DraftSet(PullRequestCmd.SetDraft cmd, List<ExecutedCommand> commands) implements PullRequestMsg {}

    /**
     * The pull request was merged or closed by running {@code commands}.
     */
    record Ended(PullRequestCmd.ChangingCmd cmd, List<ExecutedCommand> commands) implements PullRequestMsg {}

    record InfoClosed() implements PullRequestMsg {}
}
