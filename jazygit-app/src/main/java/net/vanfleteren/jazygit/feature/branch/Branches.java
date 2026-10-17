package net.vanfleteren.jazygit.feature.branch;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Questions about the branches that several branch operations ask.
 */
final class Branches {

    private Branches() {}

    /**
     * The checked out branch and {@code other}, if {@code other} is a known branch other than the checked out
     * one: what merging and rebasing work with.
     */
    static Option<Tuple2<Branch.Name, Branch.Name>> checkedOutAnd(Model model, Branch.Name other) {
        return model.repo()
                .currentBranch()
                .filter(current -> !current.name().equals(other))
                .flatMap(current -> model.idleBranch(current.name()))
                .flatMap(current -> model.idleBranch(other).map(found -> Tuple.of(current.name(), found.name())));
    }

    /**
     * Why {@code operation} cannot be done to {@code name} here, when it is a branch checked out in another
     * worktree.
     */
    static Option<AppError> checkedOutElsewhere(Model model, Operation operation, Branch.Name name) {
        return model.repo()
                .branch(name)
                .flatMap(Branch::worktreeLocation)
                .map(worktree -> new AppError.CheckedOutElsewhere(operation, name, worktree));
    }

    /**
     * Whether {@code name} is a known branch that is not checked out, and that no operation runs on.
     */
    static boolean isOtherIdleBranch(Model model, Branch.Name name) {
        return model.idleBranch(name).exists(branch -> !branch.current());
    }
}
