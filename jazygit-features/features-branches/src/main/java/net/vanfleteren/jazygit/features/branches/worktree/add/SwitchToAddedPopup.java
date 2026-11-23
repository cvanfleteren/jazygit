package net.vanfleteren.jazygit.features.branches.worktree.add;

import net.vanfleteren.jazygit.features.branches.worktree.WorktreePopup;

/**
 * Telling that the worktree for what {@code label} names was added at {@code location}, and asking to switch to it.
 * The worktrees highlight it meanwhile, once they are read again with it among them.
 */
public record SwitchToAddedPopup(String label, String location) implements WorktreePopup {}
