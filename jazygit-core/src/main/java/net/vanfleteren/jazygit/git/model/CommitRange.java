package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * The commits of a branch from one of them up to its tip, the oldest included: what moving them to another branch
 * takes away from it.
 *
 * @param commits       how many there are
 * @param merges        how many of them are merges
 * @param pushed        how many of them are on the upstream of the branch already; 0 without one
 * @param mergedThrough when the parent of the oldest, where the branch is moved back to, is not on the line of the
 *                      branch itself, that its tip follows through first parents, as the oldest came in through a
 *                      merge: the merge on that line
 */
public record CommitRange(int commits, int merges, int pushed, Option<Sha> mergedThrough) {}
