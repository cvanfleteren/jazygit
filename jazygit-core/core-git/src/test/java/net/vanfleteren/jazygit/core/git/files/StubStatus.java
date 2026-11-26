package net.vanfleteren.jazygit.core.git.files;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.GitMetadata;
import net.vanfleteren.jazygit.core.git.model.MetadataStamp;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * {@link Status} for tests: on {@code main}, without changes, and without stamps. Fakes override what they need.
 */
public interface StubStatus extends Status {

    @Override
    default Either<GitError, RepoStatus> read() {
        return Either.right(new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), List.empty()));
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> refreshIndex() {
        return Either.right(List.empty());
    }

    // No files to look at, so what the stub says is read every time.
    @Override
    default Option<GitMetadata> metadata() {
        return Option.none();
    }

    @Override
    default Option<MetadataStamp> reflogStamp() {
        return Option.none();
    }

    @Override
    default Option<MetadataStamp> diffStamp(List<FileEntry> files) {
        return Option.none();
    }

    // No files besides the changed ones, of which there are none.
    @Override
    default Either<GitError, List<RepoPath>> allFiles() {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, FileContent> content(RepoPath path) {
        return Either.left(new GitError.Unreadable(path.value(), "no such file"));
    }
}
