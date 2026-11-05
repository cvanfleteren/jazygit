package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import net.vanfleteren.jazygit.git.model.RepoStatus;

/**
 * What git status says about the working tree and the index, and the stamps that tell whether to read more (ADR 69).
 */
public interface Status {

    /**
     * The branch that is checked out, the changed files, and what the repository is in the middle of.
     */
    Either<GitError, RepoStatus> read();

    /**
     * Stamps of the files of the git directory that the branches, the stashes and the worktrees are read from,
     * taken without running git; none when they cannot be looked at, and then those are to be read every time.
     */
    Option<GitMetadata> metadata();

    /**
     * A stamp of what the diff of {@code files} is read from: the files in the working tree and the index, taken
     * without running git; none when that cannot be told, and then the diff is to be read every time (ADR 58).
     */
    Option<MetadataStamp> diffStamp(List<FileEntry> files);
}
