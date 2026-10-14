package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.git.model.Branch;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Commands that talk to GitHub, through gh.
 */
public sealed interface PullRequestCmd extends Cmd {

    /**
     * Creates a pull request from {@code branch}, as it is on its remote under the name {@code head}, into
     * {@code base}.
     */
    record CreatePullRequest(Branch.Name branch, Branch.Name head, Branch.Name base) implements PullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return Operation.CREATE_PR;
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
            return env.gitHub().createPullRequest(head.value(), base.value()).fold(
                    this::failedWith,
                    created -> new PullRequestMsg.Created(this,
                            created.number().map(number -> new PullRequest(number, head.value(), false, Checks.NONE, Review.NONE)),
                            created.commands()));
        }
    }

    /**
     * Opens pull request {@code number}, from {@code branch}, in the browser.
     */
    record OpenPullRequest(Branch.Name branch, int number) implements PullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return Operation.OPEN_PR;
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
    record MergePullRequest(Branch.Name branch, int number, MergeMethod method) implements PullRequestCmd, ChangingCmd {

        @Override
        public Operation operation() {
            return Operation.MERGE_PR;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().mergePullRequest(number, method).fold(this::failedWith, this::succeeded);
        }
    }

    /**
     * Closes pull request {@code number}, from {@code branch}, without merging it.
     */
    record ClosePullRequest(Branch.Name branch, int number) implements PullRequestCmd, ChangingCmd {

        @Override
        public Operation operation() {
            return Operation.CLOSE_PR;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().closePullRequest(number).fold(this::failedWith, this::succeeded);
        }
    }

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
     * Reads the details of pull request {@code number}, which the user is waiting for.
     */
    record LoadPullRequestDetails(int number) implements PullRequestCmd {

        @Override
        public Msg run(Env env) {
            return env.gitHub().pullRequestDetails(number).fold(
                    this::failedWith,
                    details -> new PullRequestMsg.DetailsLoaded(number, details));
        }

        @Override
        public Msg failed(Failure failure) {
            return new PullRequestMsg.DetailsFailed(number, failure);
        }
    }
}
