package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;

/**
 * The changes in the working tree and the index, as {@code git diff} shows them.
 */
public interface GitDiff {

    /**
     * The staged and the unstaged diff of {@code files}.
     */
    Either<DiffError, Diffs> diff(List<FileEntry> files);
}
