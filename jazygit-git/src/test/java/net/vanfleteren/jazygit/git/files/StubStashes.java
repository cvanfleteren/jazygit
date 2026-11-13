package net.vanfleteren.jazygit.git.files;

import static net.vanfleteren.jazygit.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Stashes} for tests: no stashes, and changing them succeeds without running anything. Fakes override what
 * they need.
 */
public interface StubStashes extends Stashes {

    @Override
    default Either<GitError, List<Stash>> list() {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, DiffText> changes(Sha sha, DiffOptions options) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<GitError, List<CommitFile>> files(Sha sha) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, DiffText> fileChanges(Sha sha, List<CommitFile> files, DiffOptions options) {
        return Either.right(DiffText.of(""));
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> push(Option<String> message) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> pop(Stash stash) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> drop(Stash stash) {
        return ran();
    }
}
