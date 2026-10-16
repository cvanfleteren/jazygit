package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;

/**
 * How a branch and another one went apart: the commits each has that the other has not, newest first.
 *
 * @param ahead       the newest commits only on the branch, at most a few hundred
 * @param aheadCount  how many commits are only on the branch, also those not in {@code ahead}
 * @param behind      the newest commits only on the other branch, at most a few hundred
 * @param behindCount how many commits are only on the other branch, also those not in {@code behind}
 */
public record Divergence(List<Commit> ahead, int aheadCount, List<Commit> behind, int behindCount) {}
