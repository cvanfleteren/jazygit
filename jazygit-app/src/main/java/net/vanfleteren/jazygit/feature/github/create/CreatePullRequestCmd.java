package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Creating the pull request of a branch: reading the branch it came from, reading the title it starts out with, and
 * creating it.
 */
public sealed interface CreatePullRequestCmd extends Cmd {

    Operation CREATE_PR =
            Operation.of("log.title.createPr", "error.createPr.failed").labelledWhenBusy("branches.busy.pullRequest");

    /**
     * A branch a pull request can merge into, as {@code name}, and {@code ref}, the branch it is on its remote.
     */
    record Base(Branch.Name name, BranchRef ref) {}

    /**
     * Reads which of {@code bases} the branch {@code branch} was made from: the one recorded as such, or else the
     * one the fewest of its commits are not on. On a tie, the earliest of {@code bases} wins. Without it, the user
     * still gets to choose.
     */
    record ReadParent(Branch.Name branch, List<Base> bases) implements CreatePullRequestCmd {

        // Further away than this, a base is too far to tell apart from the others; it also bounds the walk.
        public static final int MAX_DISTANCE = 1000;

        @Override
        public Msg run(Env env) {
            return env.git()
                    .parentBranch(branch)
                    .map(recorded -> recorded.filter(
                            name -> bases.exists(base -> base.name().equals(name))))
                    .flatMap(recorded -> recorded.isDefined() ? Either.right(recorded) : closest(env))
                    .fold(error -> read(Option.none()), this::read);
        }

        @Override
        public Msg failed(Failure failure) {
            return read(Option.none());
        }

        private Either<GitError.ProcessError, Option<Branch.Name>> closest(Env env) {
            BranchRef local = new BranchRef.Local(branch);
            return Either.sequenceRight(bases.map(base -> env.git()
                            .countNotOn(local, base.ref(), MAX_DISTANCE)
                            .map(distance -> Tuple.of(base.name(), distance))))
                    .map(distances -> distances
                            .filter(distance -> distance._2 < MAX_DISTANCE)
                            // minBy keeps the first of equals, which is the earliest base.
                            .minBy(Tuple2::_2)
                            .map(Tuple2::_1));
        }

        private Msg read(Option<Branch.Name> parent) {
            return new CreatePullRequestMsg.ParentRead(branch, parent);
        }
    }

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
