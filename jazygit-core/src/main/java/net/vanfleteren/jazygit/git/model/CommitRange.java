package net.vanfleteren.jazygit.git.model;

/**
 * The commits of a branch from one of them up to its tip, the oldest included: what moving them to another branch
 * takes away from it.
 *
 * @param commits how many there are
 * @param merges  how many of them are merges
 * @param pushed  how many of them are on the upstream of the branch already; 0 without one
 */
public record CommitRange(int commits, int merges, int pushed) {}
