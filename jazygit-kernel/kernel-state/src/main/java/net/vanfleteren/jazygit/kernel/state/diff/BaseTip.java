package net.vanfleteren.jazygit.kernel.state.diff;

import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * A branch compared with, and the commit it is at.
 */
public record BaseTip(BranchRef ref, Sha tip) {}
