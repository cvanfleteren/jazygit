package net.vanfleteren.jazygit.features.github.show;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhCommit;
import net.vanfleteren.jazygit.core.gh.model.MergeMethod;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;

/**
 * Reading the details of the pull request that is shown, and what is done to it from there.
 */
public sealed interface PullRequestCmd extends Cmd {

    Operation MERGE_PR = Operation.of("github.log.title.mergePr", "github.error.mergePr.failed")
            .labelledWhenBusy("github.branches.busy.mergePr");

    Operation ENABLE_AUTO_MERGE = Operation.of(
                    "github.log.title.enableAutoMerge", "github.error.enableAutoMerge.failed")
            .labelledWhenBusy("github.branches.busy.enableAutoMerge");

    Operation DISABLE_AUTO_MERGE = Operation.of(
                    "github.log.title.disableAutoMerge", "github.error.disableAutoMerge.failed")
            .labelledWhenBusy("github.branches.busy.disableAutoMerge");

    Operation CLOSE_PR = Operation.of("github.log.title.closePr", "github.error.closePr.failed")
            .labelledWhenBusy("github.branches.busy.closePr");

    Operation READY_PR = Operation.of("github.log.title.readyPr", "github.error.readyPr.failed")
            .labelledWhenBusy("github.branches.busy.readyPr");

    Operation DRAFT_PR = Operation.of("github.log.title.draftPr", "github.error.draftPr.failed")
            .labelledWhenBusy("github.branches.busy.draftPr");

    /**
     * An operation that ends pull request {@code number} of {@code branch}: its branch is busy meanwhile, and
     * once it is done the pull request is no longer listed as open.
     */
    sealed interface ChangingCmd extends OperationCmd {

        BranchName branch();

        PrNumber number();

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
    record OpenPullRequest(BranchName branch, PrNumber number) implements PullRequestCmd, OperationCmd {

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
            return GitHubServices.of(env)
                    .account()
                    .openPullRequest(number)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, this::succeeded);
        }
    }

    /**
     * Asks GitHub to merge pull request {@code number}, from {@code branch}, as {@code method} says, while its branch
     * is at {@code head}: GitHub merges it in the background (ADR 24).
     */
    record MergePullRequest(BranchName branch, PrNumber number, Option<GhCommit> head, MergeMethod method)
            implements PullRequestCmd, ChangingCmd {

        @Override
        public Operation operation() {
            return MERGE_PR;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .merge(number, head, method)
                    .mapLeft(GitHubFailure::of)
                    .fold(
                            this::failed,
                            merging -> new PullRequestMsg.MergeAsked(this, merging.status(), merging.commands()));
        }
    }

    /**
     * Turns the auto-merge of pull request {@code number}, from {@code branch}, on or off (ADR 112): its branch is busy
     * meanwhile, and once it is done, also when it failed, the pull request is read again.
     */
    sealed interface AutoMergeCmd extends OperationCmd {

        BranchName branch();

        PrNumber number();

        @Override
        default Option<String> subject() {
            return Option.of(branch().value());
        }

        @Override
        default Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch()));
        }

        @Override
        default boolean changesForge() {
            return true;
        }

        @Override
        default Msg succeeded(List<ExecutedCommand> commands) {
            return new PullRequestMsg.AutoMergeSet(this, commands);
        }

        @Override
        default Msg failed(Failure failure) {
            return new PullRequestMsg.AutoMergeFailed(this, failure);
        }
    }

    /**
     * Turns auto-merge on for pull request {@code number}, from {@code branch}, with node id {@code pullRequest}:
     * GitHub merges it as {@code method} says once nothing blocks it any more, when its branch is at {@code head}, if
     * given, now (ADR 112).
     */
    record EnableAutoMerge(
            BranchName branch, PrNumber number, String pullRequest, MergeMethod method, Option<GhCommit> head)
            implements PullRequestCmd, AutoMergeCmd {

        @Override
        public Operation operation() {
            return ENABLE_AUTO_MERGE;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .enableAutoMerge(pullRequest, method, head)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, this::succeeded);
        }
    }

    /**
     * Turns auto-merge off for pull request {@code number}, from {@code branch}, with node id {@code pullRequest}
     * (ADR 112).
     */
    record DisableAutoMerge(BranchName branch, PrNumber number, String pullRequest)
            implements PullRequestCmd, AutoMergeCmd {

        @Override
        public Operation operation() {
            return DISABLE_AUTO_MERGE;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .disableAutoMerge(pullRequest)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, this::succeeded);
        }
    }

    /**
     * Asks GitHub how the merge {@code id} of pull request {@code number} is doing. Nobody waits for it.
     */
    record CheckMerge(PrNumber number, String id) implements PullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("github.work.checkMerge", String.valueOf(number.value())));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .mergeStatus(number, id)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, status -> new PullRequestMsg.MergeChecked(number, id, status));
        }

        @Override
        public Msg failed(Failure failure) {
            return new PullRequestMsg.MergeCheckFailed(number, id, failure);
        }
    }

    /**
     * Closes pull request {@code number}, from {@code branch}, without merging it.
     */
    record ClosePullRequest(BranchName branch, PrNumber number) implements PullRequestCmd, ChangingCmd {

        @Override
        public Operation operation() {
            return CLOSE_PR;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .close(number)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, this::succeeded);
        }
    }

    /**
     * Turns pull request {@code number}, from {@code branch}, into a draft when {@code draft}, else marks it
     * ready for review.
     */
    record SetDraft(BranchName branch, PrNumber number, boolean draft) implements PullRequestCmd, OperationCmd {

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
        public boolean changesForge() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .setDraft(number, draft)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, this::succeeded);
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
    record LoadPullRequestDetails(PrNumber number, long read) implements PullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("state.work.pullRequest", String.valueOf(number.value())));
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .details(number)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, details -> new PullRequestMsg.DetailsLoaded(number, read, details));
        }

        @Override
        public Msg failed(Failure failure) {
            return new PullRequestMsg.DetailsFailed(number, read, failure);
        }
    }
}
