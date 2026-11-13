package net.vanfleteren.jazygit.git.files;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitError;

/**
 * {@link Diffs} for tests: nothing changed anywhere. Fakes override what they need.
 */
public interface StubDiffs extends Diffs {

    @Override
    default Either<GitError, DiffText> commitChanges(Sha sha, DiffOptions options) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<GitError, List<CommitFile>> commitFiles(Sha sha) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, DiffText> commitFileChanges(Sha sha, List<CommitFile> files, DiffOptions options) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<GitError, DiffText> pullRequestFileChanges(
            PullRequestCommits commits, List<CommitFile> files, DiffOptions options) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<GitError, List<CommitFile>> rangeFiles(Sha base, Sha tip) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, DiffText> rangeFileChanges(
            Sha base, Sha tip, List<CommitFile> files, DiffOptions options) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<GitError, net.vanfleteren.jazygit.git.model.Diffs> uncommitted(
            List<FileEntry> files, DiffOptions options) {
        return Either.right(new net.vanfleteren.jazygit.git.model.Diffs("", ""));
    }
}
