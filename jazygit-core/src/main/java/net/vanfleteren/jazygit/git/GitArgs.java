package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Revision;

/**
 * The one place a revision, a branch name or a remote is turned into text for git (ADR 40): a revision always as its
 * full ref, a full sha or {@code HEAD}, so a tag of the same name does not win, and nothing that starts with
 * {@code -}, which git would read as an option. {@code CoreArchitectureTest} keeps the other classes of this package
 * from doing it themselves.
 */
final class GitArgs {

    private GitArgs() {}

    /**
     * {@code revision}, where git takes a commit.
     */
    static String revision(Revision revision) {
        return revision.toArg();
    }

    /**
     * {@code ^<revision>}: what is not on {@code revision}, for a command that walks the history.
     */
    static String not(Revision revision) {
        return "^" + revision.toArg();
    }

    /**
     * {@code <ours>...<theirs>}: what is on one of them and not on the other.
     */
    static String symmetric(Revision ours, Revision theirs) {
        return ours.toArg() + "..." + theirs.toArg();
    }

    /**
     * {@code <source>:<target>}: the refspec that updates {@code target} with {@code source}.
     */
    static String refspec(Revision source, BranchRef target) {
        return source.toArg() + ":" + target.toArg();
    }

    /**
     * {@code branch}'s name, where git takes the name of a branch rather than a commit: the one to create, rename,
     * delete or check out, or in its configuration. A name cannot start with {@code -}.
     */
    static String name(BranchName branch) {
        return branch.value();
    }

    /**
     * {@code remote}'s name, where git takes a remote. A name cannot start with {@code -}.
     */
    static String remote(RemoteName remote) {
        return remote.value();
    }
}
