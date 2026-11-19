package net.vanfleteren.jazygit.kernel.state.view;

import net.vanfleteren.jazygit.core.git.model.BranchName;

/**
 * Why the row highlighted in the pane with the focus has no files to list, which the files pane says in their place
 * (ADR 141).
 */
public sealed interface Unlisted {

    /**
     * A branch without a base to compare it with.
     */
    record NoBase(BranchName branch) implements Unlisted {}

    /**
     * The header of a stack of pull requests: its branches list their files.
     */
    record Stack() implements Unlisted {}

    /**
     * A pull request of a stack whose branch is not checked out here.
     */
    record NotHere() implements Unlisted {}
}
