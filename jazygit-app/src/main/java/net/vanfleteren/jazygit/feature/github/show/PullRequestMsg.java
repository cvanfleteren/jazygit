package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.BranchName;
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
    record Requested(BranchName branch) implements PullRequestMsg {}

    /**
     * The user asked for a pull request listed on the pull requests screen: to show it when it is of the
     * repository, else to open it in the browser.
     */
    record SummaryRequested(PullRequestSummary pullRequest) implements PullRequestMsg {}

    /**
     * The details of pull request {@code number} were read, by read {@code read}.
     */
    record DetailsLoaded(int number, long read, PullRequestDetails details) implements PullRequestMsg {}

    record DetailsFailed(int number, long read, Failure failure) implements PullRequestMsg {}

    /**
     * The user asked to open the pull request that is shown in the browser.
     */
    record OpenConfirmed() implements PullRequestMsg {}

    /**
     * The user asked for the files the pull request that is shown changes, and what it changes in them (ADR 55).
     */
    record FilesChosen() implements PullRequestMsg {}

    /**
     * The user asked for the files the pull request of a branch changes, straight from the branch, as choosing them
     * in its dialog does.
     */
    record FilesRequested(BranchName branch) implements PullRequestMsg {}

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
     * GitHub was asked to merge the pull request, as {@code cmd} says, and said how that is doing.
     */
    record MergeAsked(PullRequestCmd.MergePullRequest cmd, MergeStatus status, List<ExecutedCommand> commands)
            implements PullRequestMsg {}

    /**
     * Periodic prompt to ask GitHub how the merges it is doing are going.
     */
    record MergeTick() implements PullRequestMsg {}

    /**
     * GitHub said how the merge {@code id} of pull request {@code number} is doing.
     */
    record MergeChecked(int number, String id, MergeStatus status) implements PullRequestMsg {}

    /**
     * Asking GitHub how the merge {@code id} of pull request {@code number} is doing failed; the next tick asks again.
     */
    record MergeCheckFailed(int number, String id, Failure failure) implements PullRequestMsg {}

    /**
     * The pull request was closed by running {@code commands}.
     */
    record Ended(PullRequestCmd.ChangingCmd cmd, List<ExecutedCommand> commands) implements PullRequestMsg {}

    /**
     * The user asked to read the pull request that is shown again.
     */
    record RefreshChosen() implements PullRequestMsg {}

    /**
     * Periodic prompt to read the pull request again, while it is shown.
     */
    record Tick() implements PullRequestMsg, Msg.Periodic {}
}
