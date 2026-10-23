package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * A branch to create along with the worktree that checks it out.
 *
 * @param start  where it starts: a local branch, or the remote-tracking ref of a remote one
 * @param parent the local branch it is made from, recorded as such; none for one made from a remote branch
 * @param track  whether it follows {@code start}, a remote branch, as checking that out does
 */
public record NewBranch(String start, Option<BranchName> parent, boolean track) {}
