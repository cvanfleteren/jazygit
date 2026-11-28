package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;

/**
 * That a commit about to be rewritten, by rewording, squashing, fixing up, dropping or amending it, is on the upstream
 * of the checked out branch already, so the next push of that branch has to be a force push (#722).
 *
 * @param upstream the remote branch the checked out branch follows, which has the commit
 * @param main     the main branch, when that has the commit too and is not the branch checked out: others build on
 *                 the commit, so the warning is a stronger one
 */
public record PushedWarning(RemoteBranch upstream, Option<BranchName> main) {}
