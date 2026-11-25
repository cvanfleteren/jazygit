package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * What one side of a conflict is, to name it by (ADR 198): never as git's ours and theirs, which in a rebase read
 * backwards.
 */
public sealed interface SideName {

    /**
     * The local branch, checked out or at whose tip the side is.
     */
    record Branch(BranchName name) implements SideName {}

    /**
     * A commit that is the tip of no local branch.
     */
    record Commit(Sha sha) implements SideName {}

    /**
     * The commit a rebase stopped at, which the app does not read yet.
     */
    record Rebased() implements SideName {}

    /**
     * What was brought onto HEAD when no operation is in progress to say what it is: a stash that was popped, a
     * squash merge or a patch applied with three-way merges.
     */
    record Incoming() implements SideName {}
}
