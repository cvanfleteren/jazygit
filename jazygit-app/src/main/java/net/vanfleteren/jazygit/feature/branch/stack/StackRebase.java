package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.AppError.StackRefusal;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.github.FromGitHub;

/**
 * What rebasing a stack on GitHub takes (ADR 74): its base, and its open branches from the bottom up, each with what
 * its own commits are told apart from; or why it is not rebased.
 *
 * @param base      what it is rebased onto: its base on the remote its branches are pushed to
 * @param layers    its open branches, from the bottom up
 * @param push      where its branches are pushed once it is rebased
 * @param back      the branch to check out again once it is done, when that is not its top
 * @param elsewhere its branches whose branch on the remote is at another commit, with that branch: whether the branch
 *                  ever had what is there is asked of git before the rebase, as one that never had it would lose it
 */
public record StackRebase(
        Revision base,
        List<StackLayer> layers,
        StackPush push,
        Option<BranchName> back,
        List<Tuple2<BranchName, RemoteBranch>> elsewhere) {

    /**
     * The number of the stack.
     */
    public int stack() {
        return push.stack();
    }

    /**
     * Its open branches, from the bottom up.
     */
    public List<BranchName> branches() {
        return layers.map(StackLayer::branch);
    }

    /**
     * What rebasing {@code stack} takes, as {@code model} has its branches; or why it is not rebased: the worktree has
     * changes, a branch of it is not checked out, is pushed to another remote than the others, is checked out in
     * another worktree, or is in a merge queue; or its base is not on that remote, or where a merged branch below was
     * is not known.
     */
    public static Either<AppError, StackRebase> of(Model model, PullRequestStack stack) {
        if (!model.repo().status().toOption().exists(status -> status.files().isEmpty())) {
            return refused(stack, new StackPush.Refused("", StackRefusal.CHANGES));
        }
        List<PullRequestStack.Entry> entries = stack.entries().sortBy(PullRequestStack.Entry::position);
        if (stack.base().isEmpty() || entries.forAll(entry -> entry.state() != State.OPEN)) {
            return refused(stack, new StackPush.Refused("#" + stack.number(), StackRefusal.NO_BASE));
        }
        Either<StackPush.Refused, StackPush> planned = StackPush.of(model, stack);
        if (planned.isLeft()) {
            return refused(stack, planned.getLeft());
        }
        StackPush push = planned.get();
        Option<StackPush.Refused> unmovable = push.branches()
                .iterator()
                .map(Tuple2::_1)
                .zip(entries.filter(StackRebase::open))
                .flatMap(branch -> unmovable(branch._1(), branch._2()))
                .headOption();
        if (unmovable.isDefined()) {
            return refused(stack, unmovable.get());
        }
        RemoteBranch base =
                new RemoteBranch(push.remote(), FromGitHub.branch(stack.base().get()));
        if (model.repo().remoteBranch(base).isEmpty()) {
            return refused(stack, new StackPush.Refused(base.shortName(), StackRefusal.BASE_NOT_FETCHED));
        }
        // The merged branches under the first open one: their commits are in the base, but not by their shas.
        Option<PullRequestStack.Entry> merged = entries.takeWhile(entry -> !open(entry))
                .filter(entry -> entry.state() == State.MERGED)
                .lastOption();
        List<Revision> belowBottom =
                merged.map(entry -> where(model, push.remote(), entry)).getOrElse(List.of(base.tracking()));
        if (belowBottom.isEmpty()) {
            return refused(stack, new StackPush.Refused("#" + merged.get().number(), StackRefusal.MERGED_UNKNOWN));
        }
        List<Tuple2<Branch, RemoteBranch>> targets = push.targets();
        List<StackLayer> layers = targets.zipWithIndex()
                .map(at -> new StackLayer(
                        at._1()._1().name(), at._2() == 0 ? belowBottom : positions(model, targets.get(at._2() - 1))));
        BranchName top = push.local().last();
        return Either.right(new StackRebase(
                base.tracking(),
                layers,
                push,
                model.repo().currentBranch().map(Branch::name).filter(current -> !current.equals(top)),
                targets.filter(target -> model.repo()
                                .remoteBranch(target._2())
                                .exists(remote ->
                                        !remote.tip().equals(target._1().tip())))
                        .map(target -> Tuple.of(target._1().name(), target._2()))));
    }

    private static boolean open(PullRequestStack.Entry entry) {
        return entry.state() == State.OPEN;
    }

    /**
     * Why {@code branch}, of the open pull request {@code entry}, cannot be moved, if it cannot: it is checked out in
     * another worktree, or is in a merge queue.
     */
    private static Option<StackPush.Refused> unmovable(Branch branch, PullRequestStack.Entry entry) {
        String name = branch.name().value();
        if (branch.checkedOutElsewhere()) {
            return Option.of(new StackPush.Refused(name, StackRefusal.OTHER_WORKTREE));
        }
        return Option.when(entry.queued(), new StackPush.Refused(name, StackRefusal.MERGE_QUEUE));
    }

    /**
     * Where the branch below, {@code below} with what it is pushed to, is: the local branch, and the branch on the
     * remote when that was fetched.
     */
    private static List<Revision> positions(Model model, Tuple2<Branch, RemoteBranch> below) {
        return List.<Revision>of(new BranchRef.Local(below._1().name()))
                .appendAll(model.repo()
                        .remoteBranch(below._2())
                        .map(found -> found.branch().tracking()));
    }

    /**
     * All that is known of where the branch of {@code entry}, a merged pull request, was: where GitHub says it was
     * last, the branch on {@code remote}, and the local branch.
     */
    private static List<Revision> where(Model model, RemoteName remote, PullRequestStack.Entry entry) {
        Option<BranchName> name = entry.headBranch().map(FromGitHub::branch);
        return entry.headOid()
                .flatMap(Sha::parse)
                .<Revision>map(Revision.Commit::new)
                .toList()
                .appendAll(name.flatMap(known -> model.repo().remoteBranch(new RemoteBranch(remote, known)))
                        .map(RemoteRef::branch)
                        .map(RemoteBranch::tracking))
                .appendAll(name.flatMap(known -> model.repo().branch(known))
                        .map(branch -> new BranchRef.Local(branch.name())));
    }

    private static <T> Either<AppError, T> refused(PullRequestStack stack, StackPush.Refused why) {
        return Either.left(new AppError.StackNotRebased(stack.number(), why.branch(), why.why()));
    }
}
