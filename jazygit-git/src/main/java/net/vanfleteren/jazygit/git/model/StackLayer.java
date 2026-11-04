package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;

/**
 * A branch of a stack to rebase (ADR 74), and what its own commits are told apart from.
 *
 * @param branch the branch
 * @param below  where the branch below it was before the rebase, or where a merged one was, or the base for the
 *               bottom: its commits are not the branch's own
 */
public record StackLayer(BranchName branch, Revision below) {

    /**
     * The own commits of a branch of a stack, oldest first, and the merges among them that a rebase of the stack
     * drops (ADR 74).
     *
     * @param branch  the branch
     * @param commits its own commits that are not merges, oldest first
     * @param merges  its own merge commits, which are left out
     */
    public record Commits(BranchName branch, List<Sha> commits, List<Merge> merges) {}

    /**
     * A merge commit a rebase of a stack drops.
     *
     * @param sha     the merge
     * @param subject the first line of its message
     */
    public record Merge(Sha sha, String subject) {}
}
