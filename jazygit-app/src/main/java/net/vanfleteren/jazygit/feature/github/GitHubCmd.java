package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commands that talk to GitHub, through gh.
 */
public sealed interface GitHubCmd extends Cmd {

    Operation CREATE_PR =
            Operation.of("log.title.createPr", "error.createPr.failed").labelledWhenBusy("branches.busy.pullRequest");

    Operation OPEN_PR = Operation.of("log.title.openPr", "error.openPr.failed");

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

        Branch.Name branch();

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
     * Reads the title a new pull request of {@code branch} into {@code base} starts out with: the subject of its
     * oldest commit that is not on {@code baseRef}, the branch {@code base} is on its remote. Without it, the
     * user still gets to enter one.
     *
     * @param head the name the branch has on its remote
     */
    record ReadTitle(Branch.Name branch, Branch.Name head, Branch.Name base, BranchRef baseRef) implements GitHubCmd {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .firstSubject(new BranchRef.Local(branch), baseRef)
                    .fold(error -> read(Option.none()), this::read);
        }

        @Override
        public Msg failed(Failure failure) {
            return read(Option.none());
        }

        private Msg read(Option<String> subject) {
            return new CreatePullRequestMsg.TitleRead(branch, head, base, subject);
        }
    }

    /**
     * Creates a pull request from {@code branch}, as it is on its remote under the name {@code head}, into
     * {@code base}, as {@code pullRequest} says.
     */
    record CreatePullRequest(Branch.Name branch, Branch.Name head, Branch.Name base, NewPullRequest pullRequest)
            implements GitHubCmd, OperationCmd {

        @Override
        public Operation operation() {
            return CREATE_PR;
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
        public Msg run(Env env) {
            return env.gitHub()
                    .createPullRequest(head.value(), base.value(), pullRequest)
                    .fold(
                            this::failedWith,
                            created -> new CreatePullRequestMsg.Created(
                                    this,
                                    created.number()
                                            .map(number -> new PullRequest(
                                                    number,
                                                    head.value(),
                                                    pullRequest.draft(),
                                                    Checks.NONE,
                                                    Review.NONE)),
                                    created.commands()));
        }
    }

    /**
     * Opens pull request {@code number}, from {@code branch}, in the browser.
     */
    record OpenPullRequest(Branch.Name branch, int number) implements GitHubCmd, OperationCmd {

        @Override
        public Operation operation() {
            return OPEN_PR;
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
     * Merges pull request {@code number}, from {@code branch}, as {@code method} says.
     */
    record MergePullRequest(Branch.Name branch, int number, MergeMethod method) implements GitHubCmd, ChangingCmd {

        @Override
        public Operation operation() {
            return MERGE_PR;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().mergePullRequest(number, method).fold(this::failedWith, this::succeeded);
        }
    }

    /**
     * Closes pull request {@code number}, from {@code branch}, without merging it.
     */
    record ClosePullRequest(Branch.Name branch, int number) implements GitHubCmd, ChangingCmd {

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
    record SetDraft(Branch.Name branch, int number, boolean draft) implements GitHubCmd, OperationCmd {

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
     * Reads the details of pull request {@code number}, which the user is waiting for.
     */
    record LoadPullRequestDetails(int number) implements GitHubCmd {

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .pullRequestDetails(number)
                    .fold(this::failedWith, details -> new PullRequestMsg.DetailsLoaded(number, details));
        }

        @Override
        public Msg failed(Failure failure) {
            return new PullRequestMsg.DetailsFailed(number, failure);
        }
    }
}
