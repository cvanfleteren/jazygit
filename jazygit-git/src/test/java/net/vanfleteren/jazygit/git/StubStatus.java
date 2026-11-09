package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

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
    default Option<MetadataStamp> diffStamp(List<FileEntry> files) {
        return Option.none();
    }
}
