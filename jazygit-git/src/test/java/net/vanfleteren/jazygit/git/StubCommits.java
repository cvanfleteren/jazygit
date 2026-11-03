package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.StubGitProvider.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Commits} for tests: creating, rewriting and moving commits succeeds without running anything. Fakes override
 * what they need.
 */
public interface StubCommits extends Commits {

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> amendLast() {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> amend(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> move(
            BranchName from, Sha oldest, BranchName to, Option<BranchRef> onto) {
        return ran();
    }

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> moveTo(BranchName from, Sha oldest, BranchName to) {
        return ran();
    }

    @Override
    default Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode) {
        return ran();
    }
}
