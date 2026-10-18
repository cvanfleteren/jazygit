package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Creating the pull request of a branch: reading the title it starts out with, and creating it.
 */
public sealed interface CreatePullRequestCmd extends Cmd {

    Operation CREATE_PR =
            Operation.of("log.title.createPr", "error.createPr.failed").labelledWhenBusy("branches.busy.pullRequest");

    /**
     * Reads the title a new pull request of {@code branch} into {@code base} starts out with: the subject of its
     * oldest commit that is not on {@code baseRef}, the branch {@code base} is on its remote. Without it, the
     * user still gets to enter one.
     *
     * @param head the name the branch has on its remote
     */
    record ReadTitle(Branch.Name branch, Branch.Name head, Branch.Name base, BranchRef baseRef)
            implements CreatePullRequestCmd {

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
            implements CreatePullRequestCmd, OperationCmd {

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
}
