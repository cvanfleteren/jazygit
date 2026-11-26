package net.vanfleteren.jazygit.core.git.files;

import static net.vanfleteren.jazygit.core.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * {@link Index} for tests: changing it succeeds without running anything. Fakes override what they need.
 */
public interface StubIndex extends Index {

    @Override
    default Either<GitFailure, List<ExecutedCommand>> stage(List<RepoPath> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> unstageNew(List<RepoPath> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> discard(DiscardPlan plan) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> unstage(List<RepoPath> paths) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> keep(RepoPath path, Conflict conflict, Conflict.Version version) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> restoreConflict(RepoPath path) {
        return ran();
    }

    @Override
    default Either<GitError, List<MarkedFile>> markers(List<RepoPath> paths) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, List<RepoPath>> unmerged(List<RepoPath> paths) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, ConflictTools> tools() {
        return Either.right(ConflictTools.NONE);
    }

    @Override
    default Either<GitError, ConflictFile> conflictFile(RepoPath path) {
        return Either.right(ConflictFile.AS_WRITTEN);
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> solve(RepoPath path, boolean rewrite) {
        return ran();
    }
}
