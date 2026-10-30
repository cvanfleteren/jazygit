package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * An operation the repository is in the middle of: it stopped, on conflicts or because it was told to, and
 * waits to be continued or aborted.
 */
public sealed interface InProgress {

    /**
     * Whether the commit it stopped at can be skipped: not for a merge, which is one step only.
     */
    default boolean skippable() {
        return !(this instanceof Merging);
    }

    /**
     * A merge of {@code theirs}, waiting to be committed or aborted.
     */
    record Merging(Sha theirs) implements InProgress {}

    /**
     * A rebase of {@code branch} onto {@code onto}, stopped at its {@code step}th of {@code total} steps.
     *
     * @param branch the branch being rebased; none for a detached HEAD
     */
    record Rebasing(Option<BranchName> branch, Sha onto, int step, int total) implements InProgress {}

    /**
     * A cherry-pick of {@code commit}.
     */
    record CherryPicking(Sha commit) implements InProgress {}

    /**
     * A revert of {@code commit}.
     */
    record Reverting(Sha commit) implements InProgress {}
}
