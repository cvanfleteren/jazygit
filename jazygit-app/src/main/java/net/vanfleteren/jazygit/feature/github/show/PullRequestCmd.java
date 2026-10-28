package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Reading the details of the pull request that is shown, and what is done to it from there.
 */
public sealed interface PullRequestCmd extends Cmd {

    Operation MERGE_PR =
            Operation.of("log.title.mergePr", "error.mergePr.failed").labelledWhenBusy("branches.busy.mergePr");

    Operation CLOSE_PR =
            Operation.of("log.title.closePr", "error.closePr.failed").labelledWhenBusy("branches.busy.closePr");

    Operation READY_PR =
            Operation.of("log.title.readyPr", "error.readyPr.failed").labelledWhenBusy("branches.busy.readyPr");

    Operation DRAFT_PR =
            Operation.of("log.title.draftPr", "error.draftPr.failed").labelledWhenBusy("branches.busy.draftPr");

    /**
     * An operation that ends pull request {@code number} of {@code branch}: its branch is busy meanwhile, and
     * once it is done the pull request is no longer listed as open.
     */
    sealed interface ChangingCmd extends OperationCmd {

        BranchName branch();

        int number();

        @Override
        default Option<String> subject() {
            return Option.of(branch().value());
        }

        @Override
        default Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch()));
        }

        @Override
        default Msg succeeded(List<ExecutedCommand> commands) {
            return new PullRequestMsg.Ended(this, commands);
        }
    }

    /**
     * Opens pull request {@code number}, from {@code branch}, in the browser.
     */
    record OpenPullRequest(BranchName branch, int number) implements PullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return BrowsePullRequestCmd.OPEN_PR;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch.value());
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().openPullRequest(number).fold(this::failedWith, this::succeeded);
        }
    }

    /**
     * Asks GitHub to merge pull request {@code number}, from {@code branch}, as {@code method} says, while its branch
     * is at {@code head}: GitHub merges it in the background (ADR 24).
     */
    record MergePullRequest(BranchName branch, int number, Option<GhCommit> head, MergeMethod method)
            implements PullRequestCmd, ChangingCmd {

        @Override
        public Operation operation() {
            return MERGE_PR;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .mergePullRequest(number, head, method)
                    .fold(
                            this::failedWith,
                            merging -> new PullRequestMsg.MergeAsked(this, merging.status(), merging.commands()));
        }
    }

    /**
     * Asks GitHub how the merge {@code id} of pull request {@code number} is doing. Nobody waits for it.
     */
    record CheckMerge(int number, String id) implements PullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.checkMerge", String.valueOf(number)));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .mergeStatus(number, id)
                    .fold(this::failedWith, status -> new PullRequestMsg.MergeChecked(number, id, status));
        }

        @Override
        public Msg failed(Failure failure) {
            return new PullRequestMsg.MergeCheckFailed(number, id, failure);
        }
    }

    /**
     * Closes pull request {@code number}, from {@code branch}, without merging it.
     */
    record ClosePullRequest(BranchName branch, int number) implements PullRequestCmd, ChangingCmd {

        @Override
        public Operation operation() {
            return CLOSE_PR;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().closePullRequest(number).fold(this::failedWith, this::succeeded);
        }
    }

    /**
     * Turns pull request {@code number}, from {@code branch}, into a draft when {@code draft}, else marks it
     * ready for review.
     */
    record SetDraft(BranchName branch, int number, boolean draft) implements PullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return draft ? DRAFT_PR : READY_PR;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch.value());
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch));
        }

        @Override
        public boolean changesGitHub() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().setDraft(number, draft).fold(this::failedWith, this::succeeded);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new PullRequestMsg.DraftSet(this, commands);
        }
    }

    /**
     * Reads the details of pull request {@code number}, which the user is waiting for, as read {@code read}: what it
     * brings is only taken while no later read was, nor a change made since it started (ADR 34).
     */
    record LoadPullRequestDetails(int number, long read) implements PullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.pullRequest", String.valueOf(number)));
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .pullRequestDetails(number)
                    .fold(this::failedWith, details -> new PullRequestMsg.DetailsLoaded(number, read, details));
        }

        @Override
        public Msg failed(Failure failure) {
            return new PullRequestMsg.DetailsFailed(number, read, failure);
        }
    }
}
