package net.vanfleteren.jazygit.core.git.commits;

import static net.vanfleteren.jazygit.core.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ResetMode;
import net.vanfleteren.jazygit.core.git.model.ResetTarget;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.CommitMessage;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * {@link Commits} for tests: creating, rewriting and moving commits succeeds without running anything. Fakes override
 * what they need.
 */
public interface StubCommits extends Commits {

    @Override
    default Either<GitFailure, List<ExecutedCommand>> commit(CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> amendLast() {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> amend(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> drop(Sha sha) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> move(
            BranchName from, Sha oldest, BranchName to, Option<BranchRef> onto) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> moveTo(BranchName from, Sha oldest, BranchName to) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode) {
        return ran();
    }
}
