package net.vanfleteren.jazygit.features.branches.branch.movecommits;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.BranchRef;

/**
 * The branch the commits move to.
 */
public sealed interface MoveTarget {

    BranchName branch();

    /**
     * A new branch, which starts at {@code onto}, onto which the commits are copied, or else where they are.
     */
    record NewBranch(BranchName branch, Option<BranchRef> onto) implements MoveTarget {}

    /**
     * An existing branch, onto which the commits are copied.
     */
    record Existing(BranchName branch) implements MoveTarget {}
}
