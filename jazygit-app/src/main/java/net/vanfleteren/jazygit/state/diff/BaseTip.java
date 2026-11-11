package net.vanfleteren.jazygit.state.diff;

import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * A branch compared with, and the commit it is at.
 */
public record BaseTip(BranchRef ref, Sha tip) {}
