package net.vanfleteren.jazygit.kernel.state.edit;

import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * A file an editor opens (ADR 206): one of the working tree, by its path in the repository, or one elsewhere on disk,
 * such as the log of a check a forge kept, by its absolute path. Only a file of the working tree can be in conflict.
 * The command that opens it resolves it against the working tree, which leaves an absolute path as it is.
 */
public sealed interface EditedFile {

    /**
     * The file at {@code path} in the working tree.
     */
    static EditedFile of(RepoPath path) {
        return new InWorktree(path);
    }

    /**
     * The file at the absolute {@code path}, outside the working tree.
     */
    static EditedFile onDisk(String path) {
        return new OnDisk(path);
    }

    /**
     * Its path as an editor run in the working tree is handed it and the log of commands shows it: relative to the
     * working tree for a file of it, else absolute.
     */
    String shown();

    /**
     * Its name, without its directories.
     */
    default String name() {
        return shown().substring(shown().lastIndexOf('/') + 1);
    }

    /**
     * The file at {@code path} in the working tree.
     */
    record InWorktree(RepoPath path) implements EditedFile {

        @Override
        public String shown() {
            return path.value();
        }
    }

    /**
     * The file at the absolute {@code path}, outside the working tree, as the command that wrote it named it.
     */
    record OnDisk(String path) implements EditedFile {

        @Override
        public String shown() {
            return path;
        }
    }
}
