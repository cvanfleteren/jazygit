package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Telling that the worktree for {@code branch} was added at {@code location}, and asking to switch to it.
 */
public record SwitchToAddedPopup(Branch.Name branch, String location) implements Popup {}
