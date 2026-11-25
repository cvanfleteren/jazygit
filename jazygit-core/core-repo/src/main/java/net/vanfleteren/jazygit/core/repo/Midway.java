package net.vanfleteren.jazygit.core.repo;

/**
 * What a worktree is in the middle of, and how many files there still have conflicts (ADR 197).
 *
 * @param inProgress the operation it stopped in the middle of
 * @param conflicts  how many files still have conflicts; 0 when they could not be counted
 */
public record Midway(InProgress inProgress, int conflicts) {}
