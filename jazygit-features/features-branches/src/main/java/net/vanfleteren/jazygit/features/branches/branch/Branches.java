package net.vanfleteren.jazygit.features.branches.branch;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Midway;
import net.vanfleteren.jazygit.core.repo.Worktree;
import net.vanfleteren.jazygit.features.branches.worktree.WorktreeError;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Questions about the branches that several branch operations ask.
 */
public final class Branches {

    private Branches() {}

    /**
     * The checked out branch and {@code other}, if {@code other} is a known branch, local or remote-tracking, other
     * than the checked out one: what merging and rebasing work with.
     */
    public static Option<Tuple2<BranchName, BranchRef>> checkedOutAnd(Model model, BranchRef other) {
        return model.repo()
                .currentBranch()
                .filter(current -> !other.equals(new BranchRef.Local(current.name())))
                .flatMap(current -> model.idleBranch(current.name()))
                .flatMap(current -> idle(model, other).map(found -> Tuple.of(current.name(), found)));
    }

    /**
     * Whether {@code ref} is a known branch, local or remote-tracking.
     */
    public static boolean known(Model model, BranchRef ref) {
        return switch (ref) {
            case BranchRef.Local local -> model.repo().branch(local.branch()).isDefined();
            case BranchRef.Remote remote ->
                model.repo().remoteBranch(remote.branch()).isDefined();
        };
    }

    /**
     * {@code ref}, if it is a known branch that no operation runs on.
     */
    public static Option<BranchRef> idle(Model model, BranchRef ref) {
        return switch (ref) {
            case BranchRef.Local local -> model.idleBranch(local.branch()).map(known -> ref);
            case BranchRef.Remote remote ->
                model.repo()
                        .remoteBranch(remote.branch())
                        .filter(known -> !model.jobs().busy().containsKey(ref))
                        .map(known -> ref);
        };
    }

    /**
     * Why {@code operation} cannot be done to {@code name} here, when it is a branch checked out in another
     * worktree.
     */
    public static Option<AppError> checkedOutElsewhere(Model model, Operation operation, BranchName name) {
        return model.repo()
                .branch(name)
                .flatMap(Branch::worktreeLocation)
                .map(worktree -> new WorktreeError.CheckedOutElsewhere(operation, name, worktree));
    }

    /**
     * Whether {@code name} is a known branch that is not checked out, and that no operation runs on.
     */
    public static boolean isOtherIdleBranch(Model model, BranchName name) {
        return model.idleBranch(name).exists(branch -> !branch.current());
    }

    /**
     * The branch the worktree shown rebases, with its HEAD detached meanwhile, if it does: the one it works on,
     * which is shown as checked out (ADR 197).
     */
    public static Option<BranchName> rebasedHere(Model model) {
        return model.repo().status().toOption().flatMap(RepoStatus::rebasedBranch);
    }

    /**
     * What the worktree that works on {@code branch} is in the middle of, if anything (ADR 197): this one, for the
     * branch checked out or rebased here; another one, for a branch checked out or rebased there, once the worktrees
     * are read.
     */
    public static Option<Midway> midway(Model model, Branch branch) {
        return branch.worktree()
                .map(location -> model.repo()
                        .worktrees()
                        .toOption()
                        .flatMap(worktrees ->
                                worktrees.find(worktree -> worktree.path().equals(location)))
                        .flatMap(Worktree::midway))
                .getOrElse(() -> branch.current() || rebasedHere(model).contains(branch.name())
                        ? model.repo().status().toOption().flatMap(RepoStatus::midway)
                        : Option.none());
    }
}
