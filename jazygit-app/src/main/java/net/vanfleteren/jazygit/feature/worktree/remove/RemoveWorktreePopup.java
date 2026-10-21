package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking whether to remove the worktree at {@code location}, and whether to delete its branch with it.
 *
 * @param name   the name of its directory
 * @param branch the branch it has checked out, if any
 */
public record RemoveWorktreePopup(String location, String name, Option<Branch.Name> branch) implements Popup {}
