package net.vanfleteren.jazygit.state.github;

import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * What GitHub's branches are called in git: the one place a branch gh read is named for git, the way back of
 * {@code OnGitHub}. It is the name of the branch on the remote, which may not be that of the local branch that pushes
 * to it (ADR 40).
 */
public final class FromGitHub {

    private FromGitHub() {}

    /**
     * {@code branch}, as git names it on the remote.
     */
    public static BranchName branch(GhBranch branch) {
        return new BranchName(branch.name());
    }
}
