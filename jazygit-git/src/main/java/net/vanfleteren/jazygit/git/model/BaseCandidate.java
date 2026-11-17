package net.vanfleteren.jazygit.git.model;

/**
 * A branch that another one could be built on, with how their histories part (ADR 143).
 *
 * @param name    the branch, as {@code branch.<name>.gh-merge-base} records it
 * @param counted the branch the counts are of: the one on {@code origin} when it is there, else the local one, as
 *                the base of a branch is looked up (ADR 20)
 * @param own     the commits of the other branch that this one does not have: those since they forked
 * @param theirs  the commits of this branch that the other one does not have
 */
public record BaseCandidate(BranchName name, BranchRef counted, int own, int theirs) {}
