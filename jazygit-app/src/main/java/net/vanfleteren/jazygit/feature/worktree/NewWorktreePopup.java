package net.vanfleteren.jazygit.feature.worktree;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where the new worktree for {@code branch} goes.
 *
 * @param location where it goes unless the user says otherwise
 */
public record NewWorktreePopup(Branch.Name branch, String location) implements Popup {}
