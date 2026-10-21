package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.WorktreePopup;

/**
 * Telling that the worktree for {@code branch} was added at {@code location}, and asking to switch to it. The
 * worktrees highlight it meanwhile, once they are read again with it among them.
 */
public record SwitchToAddedPopup(Branch.Name branch, String location) implements WorktreePopup {}
