package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitFailureTest {

    private static final GitError.CommandFailed REFUSED = new GitError.CommandFailed("error: refused", 1);
    private static final GitError.CommandFailed ABORT_FAILED = new GitError.CommandFailed("fatal: no rebase", 128);

    @Test
    void aCleanupThatSucceedsOnlyAddsItsCommands() {
        GitFailure<GitError.ProcessError> failure = new GitFailure<>(REFUSED, List.of("git rebase main"));

        assertEquals(new GitFailure<>(REFUSED, List.of("git rebase main", "git rebase --abort")),
                failure.followedBy(Either.right(List.of("git rebase --abort"))));
    }

    @Test
    void aCleanupThatFailsIsReportedNextToTheFailure() {
        GitFailure<GitError.ProcessError> failure = new GitFailure<>(REFUSED, List.of("git rebase main"));

        GitFailure<GitError.ProcessError> cleanedUp = failure.followedBy(
                Either.left(new GitFailure<>(ABORT_FAILED, List.of("git rebase --abort"))));

        assertEquals(REFUSED, cleanedUp.error());
        assertEquals(List.of("git rebase main", "git rebase --abort"), cleanedUp.commands());
        assertEquals(Option.of(ABORT_FAILED), cleanedUp.cleanupFailed());
    }

    @Test
    void aSequenceStopsAtTheFirstFailureKeepingTheEarlierCommands() {
        Either<GitFailure<GitError.ProcessError>, List<String>> outcome = GitFailure.sequence(
                () -> Either.right(List.of("git add a")),
                () -> Either.left(new GitFailure<>(REFUSED, List.of("git commit"))),
                () -> Either.right(List.of("git push")));

        assertEquals(new GitFailure<>(REFUSED, List.of("git add a", "git commit")), outcome.getLeft());
    }
}
