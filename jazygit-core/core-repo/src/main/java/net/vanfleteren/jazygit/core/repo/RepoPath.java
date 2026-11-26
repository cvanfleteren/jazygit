package net.vanfleteren.jazygit.core.repo;

/**
 * A file or directory in the repository, by its path from the top of the worktree with {@code /} between its parts,
 * e.g. {@code src/Main.java}, as git writes it. Unlike a {@link java.nio.file.Path}, it is not anchored anywhere on
 * disk; the worktree it is in resolves it.
 */
public record RepoPath(String value) implements Comparable<RepoPath> {

    /**
     * Ordered by path, as git lists files.
     */
    @Override
    public int compareTo(RepoPath other) {
        return value.compareTo(other.value);
    }

    /**
     * Whether this is below {@code directory}; everything is below the top of the worktree, whose path is empty.
     */
    public boolean isIn(RepoPath directory) {
        return directory.value.isEmpty() || value.startsWith(directory.value + "/");
    }
}
