package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;

/**
 * A branch of a stack to rebase (ADR 74), and what its own commits are told apart from.
 *
 * @param branch the branch
 * @param below  every place the branch below it is known to be or to have been: its tip before the rebase and its
 *               branch on the remote, or all that is known of where a merged one was, or the base for the bottom.
 *               Their commits are not the branch's own, nor are those of where the branch below was when this one
 *               forked from it, as its reflog says
 */
public record StackLayer(BranchName branch, List<Revision> below) {

    /**
     * The own commits of a branch of a stack, oldest first, and the merges among them that a rebase of the stack
     * drops (ADR 74).
     *
     * @param branch  the branch
     * @param tip     where the branch was when they were read: the rebase refuses to start once it moved
     * @param commits its own commits that are not merges, oldest first
     * @param merges  its own merge commits, which are left out
     */
    public record Commits(BranchName branch, Sha tip, List<Sha> commits, List<Merge> merges) {}

    /**
     * A merge commit a rebase of a stack drops.
     *
     * @param sha     the merge
     * @param subject the first line of its message
     */
    public record Merge(Sha sha, String subject) {}
}
