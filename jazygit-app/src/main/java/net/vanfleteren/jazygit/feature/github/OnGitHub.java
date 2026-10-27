package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * What git's branches are called on GitHub: the one place a branch is named for gh, from the branch on the remote, so
 * the name of a local branch is not handed to gh for it (ADR 40). {@code ArchitectureTest} keeps the rest of the app
 * from naming one itself.
 */
public final class OnGitHub {

    private OnGitHub() {}

    /**
     * {@code remote}, as GitHub names it in the repository.
     */
    public static GhBranch branch(RemoteBranch remote) {
        return new GhBranch(remote.branch().value());
    }
}
