package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchError.StackRefusal;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.github.FromGitHub;

/**
 * Where the open branches of a stack on GitHub are pushed (ADR 74): all to one remote, in one push, each to the
 * branch of its pull request there, which is not always its own name.
 *
 * @param stack    the number of the stack
 * @param remote   the remote its branches are all pushed to
 * @param branches its open branches, from the bottom up, each with the head of its pull request
 */
public record StackPush(int stack, RemoteName remote, List<Tuple2<Branch, BranchName>> branches) {

    /**
     * Its local branches, from the bottom up.
     */
    public List<BranchName> local() {
        return branches.map(branch -> branch._1().name());
    }

    /**
     * Each local branch with the branch on {@link #remote} it is pushed to.
     */
    public List<Tuple2<BranchName, BranchName>> pushed() {
        return branches.map(branch -> Tuple.of(branch._1().name(), branch._2()));
    }

    /**
     * Each local branch with the branch on {@link #remote} it is pushed to, as a remote branch.
     */
    public List<Tuple2<Branch, RemoteBranch>> targets() {
        return branches.map(branch -> Tuple.of(branch._1(), new RemoteBranch(remote, branch._2())));
    }

    /**
     * Pushing the open branches of {@code stack}, as {@code model} has them; or why not: a pull request of it has no
     * local branch or no known branch on GitHub, or they are not all pushed to the same remote.
     */
    public static Either<Refused, StackPush> of(Model model, PullRequestStack stack) {
        List<PullRequestStack.Entry> open =
                stack.entries().filter(entry -> entry.state() == State.OPEN).sortBy(PullRequestStack.Entry::position);
        Option<PullRequestStack.Entry> missing =
                open.find(entry -> checkedOut(model, entry).isEmpty());
        if (missing.isDefined()) {
            return Either.left(new Refused("#" + missing.get().number().value(), StackRefusal.NOT_CHECKED_OUT));
        }
        Option<PullRequestStack.Entry> unnamed =
                open.find(entry -> entry.headBranch().isEmpty());
        if (unnamed.isDefined()) {
            return Either.left(new Refused("#" + unnamed.get().number().value(), StackRefusal.UNKNOWN_BRANCH));
        }
        List<Tuple2<Branch, BranchName>> branches = open.map(entry -> Tuple.of(
                checkedOut(model, entry).get(),
                FromGitHub.branch(entry.headBranch().get())));
        Option<RemoteName> remote =
                branches.headOption().map(branch -> branch._1().pushTarget().remote());
        Option<Branch> elsewhere = branches.map(Tuple2::_1)
                .find(branch -> !remote.contains(branch.pushTarget().remote()));
        if (elsewhere.isDefined()) {
            return Either.left(new Refused(elsewhere.get().name().value(), StackRefusal.OTHER_REMOTE));
        }
        return Either.right(new StackPush(stack.number(), remote.getOrElse(RemoteBranch.DEFAULT_REMOTE), branches));
    }

    /**
     * The local branch of {@code entry}, a pull request of a stack: the one whose pull request it is, by its upstream
     * or its name.
     */
    static Option<Branch> checkedOut(Model model, PullRequestStack.Entry entry) {
        return model.repo()
                .branches()
                .toOption()
                .getOrElse(List.empty())
                .find(branch -> model.gitHub()
                        .pullRequest(branch)
                        .exists(pr -> pr.number().equals(entry.number())));
    }

    /**
     * Why a stack is not pushed or rebased, naming the branch or pull request it is about.
     *
     * @param branch the branch, or {@code #<number>} of a pull request without one
     * @param why    why
     */
    public record Refused(String branch, StackRefusal why) {}
}
