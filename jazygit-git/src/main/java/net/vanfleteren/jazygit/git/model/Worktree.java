package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;
import java.nio.file.Path;

/**
 * A working tree of the repository: the main one, or one linked to it with {@code git worktree add}.
 *
 * @param path     the root of the working tree
 * @param head     the branch or the commit it has checked out; none when git cannot resolve its HEAD, as while
 *                 {@code git worktree add} sets it up, or when its HEAD file is broken. The commit of a branch is
 *                 where the branch was when the worktrees were listed: a commit on it does not list them again
 *                 (ADR 57)
 * @param main     whether it is the main worktree, the one the repository lives in, which cannot be removed
 * @param current  whether it is the one the app shows
 * @param locked   why it is locked, which keeps git from removing it, if it is; empty when no reason was given
 * @param prunable whether its directory is gone, so that git only remembers it
 */
public record Worktree(
        Path path, Option<Head> head, boolean main, boolean current, Option<String> locked, boolean prunable) {

    /**
     * The branch it has checked out; none when its HEAD is detached or cannot be resolved.
     */
    public Option<BranchName> branch() {
        return head.flatMap(Head::branchName);
    }

    /**
     * Whether it is locked.
     */
    public boolean isLocked() {
        return locked.isDefined();
    }

    /**
     * Where it is, as text: what identifies it in messages and popups.
     */
    public String location() {
        return path.toString();
    }

    /**
     * The name of its directory.
     */
    public String name() {
        // The root directory has no name.
        return Option.of(path.getFileName()).map(Path::toString).getOrElse(this::location);
    }

    /**
     * Where a new worktree for {@code branch} goes by default: next to this one, named after it and the branch,
     * e.g. {@code /repos/app-feature-login} for {@code feature/login} next to {@code /repos/app}.
     */
    public String siblingFor(BranchName branch) {
        return path.resolveSibling(name() + "-" + directoryFor(branch)).toString();
    }

    /**
     * Where a new worktree for {@code branch} goes next to this one when there is no main one to name it after, as
     * next to a bare repository: named after the branch, e.g. {@code /repos/proj/feature-login} for
     * {@code feature/login} next to {@code /repos/proj/main}.
     */
    public String nextTo(BranchName branch) {
        return path.resolveSibling(directoryFor(branch)).toString();
    }

    private static String directoryFor(BranchName branch) {
        return branch.value().replace('/', '-');
    }
}
