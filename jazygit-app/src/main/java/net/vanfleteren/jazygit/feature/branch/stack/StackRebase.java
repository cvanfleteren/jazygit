package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.AppError.StackRefusal;
import net.vanfleteren.jazygit.state.Model;

/**
 * What rebasing a stack on GitHub takes (ADR 74): its base, and its open branches from the bottom up, each with what
 * its own commits are told apart from; or why it is not rebased.
 *
 * @param stack  the number of the stack
 * @param base   what it is rebased onto: its base on the remote, else the local branch
 * @param layers its open branches, from the bottom up
 * @param back   the branch to check out again once it is done, when that is not its top
 * @param unfollowed its branches without an upstream that are on the remote at another commit: whether that has
 *                   commits the branch lacks is asked of git before the rebase, as {@code behind} only knows an
 *                   upstream
 */
public record StackRebase(
        int stack, Revision base, List<StackLayer> layers, Option<BranchName> back, List<RemoteBranch> unfollowed) {

    /**
     * Its open branches, from the bottom up.
     */
    public List<BranchName> branches() {
        return layers.map(StackLayer::branch);
    }

    /**
     * What rebasing {@code stack} takes, as {@code model} has its branches; or why it is not rebased: a branch of it
     * is checked out in another worktree, is in a merge queue, is not checked out, or is behind its branch on the
     * remote; or where a merged branch below was, or its base, is not known.
     */
    public static Either<AppError, StackRebase> of(Model model, PullRequestStack stack) {
        List<Branch> local = model.repo().branches().toOption().getOrElse(List.empty());
        List<PullRequestStack.Entry> entries = stack.entries().sortBy(PullRequestStack.Entry::position);
        List<PullRequestStack.Entry> open = entries.filter(entry -> entry.state() == State.OPEN);
        Option<Revision> base = stack.base().flatMap(name -> onRemoteElseLocal(model, local, new BranchName(name)));
        if (base.isEmpty() || open.isEmpty()) {
            return refused(stack, "#" + stack.number(), StackRefusal.NO_BASE);
        }
        // The merged branches under the first open one: their commits are in the base, but not by their shas.
        Option<PullRequestStack.Entry> merged = entries.takeWhile(entry -> entry.state() != State.OPEN)
                .filter(entry -> entry.state() == State.MERGED)
                .lastOption();
        Option<Revision> belowBottom = merged.isEmpty() ? base : merged.flatMap(entry -> where(model, local, entry));
        if (belowBottom.isEmpty()) {
            return refused(stack, "#" + merged.get().number(), StackRefusal.MERGED_UNKNOWN);
        }
        List<Either<AppError, Branch>> branches = open.map(entry -> branchOf(model, local, stack, entry));
        Option<AppError> refusal = branches.find(Either::isLeft).map(Either::getLeft);
        if (refusal.isDefined()) {
            return Either.left(refusal.get());
        }
        List<Branch> stacked = branches.map(Either::get);
        List<StackLayer> layers = stacked.zipWithIndex()
                .map(at -> new StackLayer(
                        at._1().name(),
                        at._2() == 0
                                ? belowBottom.get()
                                : new Revision.Commit(stacked.get(at._2() - 1).tip())));
        BranchName top = stacked.last().name();
        return Either.right(new StackRebase(
                stack.number(),
                base.get(),
                layers,
                model.repo().currentBranch().map(Branch::name).filter(current -> !current.equals(top)),
                stacked.filter(branch -> branch.upstream().isEmpty()).flatMap(branch -> unfollowed(model, branch))));
    }

    /**
     * {@code branch}, without an upstream, on the default remote, when it is there at another commit: an agent can
     * push it without setting one, and pushing over it would lose what it has that the branch has not (ADR 74).
     */
    private static Option<RemoteBranch> unfollowed(Model model, Branch branch) {
        return model.repo()
                .remoteBranch(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, branch.name()))
                .filter(remote -> !remote.tip().equals(branch.tip()))
                .map(RemoteRef::branch);
    }

    /**
     * The local branch of {@code entry}, an open pull request of {@code stack}; or why it is not rebased.
     */
    private static Either<AppError, Branch> branchOf(
            Model model, List<Branch> local, PullRequestStack stack, PullRequestStack.Entry entry) {
        Option<Branch> branch =
                local.find(b -> model.gitHub().pullRequest(b).exists(pr -> pr.number() == entry.number()));
        if (branch.isEmpty()) {
            return refused(stack, "#" + entry.number(), StackRefusal.NOT_CHECKED_OUT);
        }
        Branch found = branch.get();
        String name = found.name().value();
        if (found.checkedOutElsewhere()) {
            return refused(stack, name, StackRefusal.OTHER_WORKTREE);
        }
        if (entry.queued()) {
            return refused(stack, name, StackRefusal.MERGE_QUEUE);
        }
        if (found.behind() > 0) {
            return refused(stack, name, StackRefusal.BEHIND_REMOTE);
        }
        return Either.right(found);
    }

    /**
     * Where the branch of {@code entry}, a merged pull request, was: the local branch, the branch on the remote, or
     * where GitHub says it was last.
     */
    private static Option<Revision> where(Model model, List<Branch> local, PullRequestStack.Entry entry) {
        BranchName name = new BranchName(entry.headBranch());
        return local.find(b -> b.name().equals(name))
                .<Revision>map(b -> new Revision.Commit(b.tip()))
                .orElse(() -> model.repo()
                        .remoteBranch(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, name))
                        .map(remote -> new BranchRef.Remote(remote.branch())))
                .orElse(() -> entry.headOid().flatMap(Sha::parse).map(Revision.Commit::new));
    }

    /**
     * {@code name} on the default remote, when it is there; else the local branch of that name.
     */
    private static Option<Revision> onRemoteElseLocal(Model model, List<Branch> local, BranchName name) {
        return model.repo()
                .remoteBranch(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, name))
                .<Revision>map(remote -> new BranchRef.Remote(remote.branch()))
                .orElse(() -> local.find(b -> b.name().equals(name)).map(b -> new BranchRef.Local(b.name())));
    }

    private static <T> Either<AppError, T> refused(PullRequestStack stack, String branch, StackRefusal why) {
        return Either.left(new AppError.StackNotRebased(stack.number(), branch, why));
    }
}
