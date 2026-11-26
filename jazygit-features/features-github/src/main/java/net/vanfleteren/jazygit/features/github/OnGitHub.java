package net.vanfleteren.jazygit.features.github;

import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;

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
    public static ForgeBranch branch(RemoteBranch remote) {
        return new ForgeBranch(remote.branch().value());
    }
}
