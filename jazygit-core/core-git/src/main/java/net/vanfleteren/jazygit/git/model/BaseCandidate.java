package net.vanfleteren.jazygit.git.model;

/**
 * A branch that another one could be built on, with how their histories part (ADR 143): the other branch is
 * {@code ahead} and {@code behind} this one, as its title would say were this its base.
 *
 * @param name    the branch, as {@code branch.<name>.gh-merge-base} records it
 * @param counted the branch the counts are of: the one on {@code origin} when it is there, else the local one, as
 *                the base of a branch is looked up (ADR 20)
 * @param ahead   the commits of the other branch that this one does not have: those since they forked
 * @param behind  the commits of this one that the other branch does not have: this one's own
 */
public record BaseCandidate(BranchName name, BranchRef counted, int ahead, int behind) {}
