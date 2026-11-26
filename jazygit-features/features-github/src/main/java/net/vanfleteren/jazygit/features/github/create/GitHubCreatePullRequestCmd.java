package net.vanfleteren.jazygit.features.github.create;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.features.branches.branch.base.SetBaseCmd;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.OnGitHub;
import net.vanfleteren.jazygit.features.github.stack.StackCmd;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;

/**
 * Creating the pull request of a branch on GitHub: reading the branch it came from, recording it to stack its pull
 * request, and creating it. Pushing it first and reading its title are {@code CreatePullRequestCmd}'s.
 */
public sealed interface GitHubCreatePullRequestCmd extends Cmd {

    Operation CREATE_PR = Operation.of("github.log.title.createPr", "github.error.createPr.failed")
            .labelledWhenBusy("github.branches.busy.pullRequest");

    /**
     * A branch a pull request can merge into, as {@code name}, and {@code ref}, the branch it is on its remote.
     */
    record Base(BranchName name, BranchRef ref) {

        /**
         * The name the branch has on its remote, which is what gh and the recorded parent call it; its own name
         * when it is not on one.
         */
        BranchName remoteName() {
            return remote().branch();
        }

        /**
         * The branch on its remote, which gh merges into: the one it is, or the one of its name on the default
         * remote when it is not on one.
         */
        RemoteBranch remote() {
            return ref instanceof BranchRef.Remote(RemoteBranch remote)
                    ? remote
                    : new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, name);
        }
    }

    /**
     * Where a new pull request goes in a stack once it is created (ADR 73): on pull request {@code below}, a new stack
     * of the two, or on the top of its stack {@code stack}.
     */
    record Stacking(PrNumber below, Option<StackId> stack) {

        /**
         * Puts the pull request {@code number} on {@code below}.
         */
        StackCmd.MakeStack cmd(PrNumber number) {
            return new StackCmd.MakeStack(
                    List.empty(), stack.isDefined() ? List.of(number) : List.of(below, number), stack);
        }
    }

    /**
     * Why a new pull request cannot be stacked on pull request {@code below}: it is in stack {@code stack}, but not on
     * its top, where only GitHub adds.
     */
    record NotTop(PrNumber below, StackId stack) {}

    /**
     * Reads which of {@code bases} the branch {@code branch} was made from: the one recorded as such, or else the
     * one the fewest of its commits are not on. On a tie, the earliest of {@code bases} wins. Without it, the user
     * still gets to choose.
     */
    record ReadParent(BranchName branch, List<Base> bases) implements GitHubCreatePullRequestCmd {

        // Further away than this, a base is too far to tell apart from the others; it also bounds the walk.
        public static final int MAX_DISTANCE = 1000;

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("github.work.base", branch.value()));
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .branches()
                    .parent(branch)
                    .map(recorded -> recorded.flatMap(this::base))
                    .flatMap(recorded -> recorded.isDefined() ? Either.right(recorded) : closest(env))
                    .fold(error -> read(Option.none()), this::read);
        }

        @Override
        public Msg failed(Failure failure) {
            return read(Option.none());
        }

        // The recorded parent is a name on the remote, or a local one when the branch was made from a local branch.
        private Option<BranchName> base(BranchName recorded) {
            return bases.find(base -> base.remoteName().equals(recorded))
                    .orElse(() -> bases.find(base -> base.name().equals(recorded)))
                    .map(Base::name);
        }

        private Either<GitError, Option<BranchName>> closest(Env env) {
            BranchRef local = new BranchRef.Local(branch);
            return Either.sequenceRight(bases.map(base -> env.git()
                            .history()
                            .countNotOn(local, base.ref(), MAX_DISTANCE)
                            .map(distance -> Tuple.of(base.name(), distance))))
                    .map(distances -> distances
                            .filter(distance -> distance._2 < MAX_DISTANCE)
                            // minBy keeps the first of equals, which is the earliest base.
                            .minBy(Tuple2::_2)
                            .map(Tuple2::_1));
        }

        private Msg read(Option<BranchName> parent) {
            return new GitHubCreatePullRequestMsg.ParentRead(branch, parent);
        }
    }

    /**
     * Records the base of a branch as its parent, as {@code set} does, before its pull request is created to put it on
     * a stack (ADR 148): the create dialog then offers that base first, also after pushing the branch.
     */
    record RecordParent(SetBaseCmd set) implements GitHubCreatePullRequestCmd, BranchCmd {

        @Override
        public BranchName branch() {
            return set.branch();
        }

        @Override
        public Operation operation() {
            return set.operation();
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return set.perform(git);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new GitHubCreatePullRequestMsg.ParentRecorded(this, commands);
        }
    }

    /**
     * Creates a pull request from {@code branch}, as it is on its remote under the name {@code head}, into
     * {@code base}, the name that branch has on its remote, as {@code pullRequest} says.
     */
    record CreatePullRequest(
            BranchName branch,
            RemoteBranch head,
            RemoteBranch base,
            NewPullRequest pullRequest,
            Option<Stacking> stacking)
            implements GitHubCreatePullRequestCmd, OperationCmd {

        /**
         * One that is stacked on nothing.
         */
        public CreatePullRequest(BranchName branch, RemoteBranch head, RemoteBranch base, NewPullRequest pullRequest) {
            this(branch, head, base, pullRequest, Option.none());
        }

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
            return GitHubServices.of(env)
                    .pullRequests()
                    .create(OnGitHub.branch(head), OnGitHub.branch(base), pullRequest)
                    .mapLeft(GitHubFailure::of)
                    .fold(
                            this::failed,
                            created -> new GitHubCreatePullRequestMsg.Created(
                                    this,
                                    created.number()
                                            .map(number -> new PullRequest(
                                                    number,
                                                    OnGitHub.branch(head),
                                                    Option.some(OnGitHub.branch(base)),
                                                    pullRequest.draft(),
                                                    Checks.NONE,
                                                    Review.NONE,
                                                    Option.none(),
                                                    Option.none())),
                                    created.commands()));
        }
    }
}
