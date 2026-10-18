package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.git.model.BranchRef;

/**
 * Where the new branch of the moved commits starts.
 */
public sealed interface MoveBase {

    /**
     * At the parent of the oldest moved commit: the commits stay where they are, so nothing is copied.
     */
    record Parent() implements MoveBase {}

    /**
     * At {@code branch}, onto which the commits are cherry-picked.
     */
    record Onto(BranchRef branch) implements MoveBase {}
}
