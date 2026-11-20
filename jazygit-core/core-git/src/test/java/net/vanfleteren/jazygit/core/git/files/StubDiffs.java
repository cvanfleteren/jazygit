package net.vanfleteren.jazygit.core.git.files;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.Sha;

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
    default Either<GitError, List<String>> treeFiles(Sha sha) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, FileContent> treeContent(Sha sha, String path) {
        return Either.left(new GitError.Unreadable(path, "not in " + sha.shortSha()));
    }

    @Override
    default Either<GitError, String> treeText(Sha sha, String path) {
        return Either.left(new GitError.Unreadable(path, "not in " + sha.shortSha()));
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
    default Either<GitError, net.vanfleteren.jazygit.core.git.model.Diffs> uncommitted(
            List<FileEntry> files, DiffOptions options) {
        return Either.right(new net.vanfleteren.jazygit.core.git.model.Diffs("", ""));
    }
}
