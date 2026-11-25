package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The checks of a commit, folded from the statuses posted for it (ADR 195).
 */
class ForgejoCheckTest {

    private static final String JOB = "https://code.example.net/cvf/nix/actions/runs/12/jobs/1";
    private static final Instant T0 = Instant.parse("2026-11-25T10:00:00Z");

    private static ForgejoCheck.Posted posted(long id, String context, String status, String description, int at) {
        return new ForgejoCheck.Posted(id, context, status, description, Option.of(JOB), T0.plusSeconds(at));
    }

    @Test
    void aJobDoneIsTimedFromWhenItStartedRunningToItsLastStatus() {
        ForgejoCheck check = ForgejoCheck.of(List.of(
                        posted(3, "ci / test (push)", "failure", "Failing after 50s", 70),
                        posted(1, "ci / test (push)", "pending", "Waiting to run", 0),
                        posted(2, "ci / test (push)", "pending", "Has started running", 20)))
                .single();
        assertEquals(CommitStatus.State.FAILED, check.state());
        assertEquals(T0, check.created());
        assertEquals(Option.of(T0.plusSeconds(20)), check.started());
        assertEquals(Option.of(T0.plusSeconds(70)), check.ended());
        assertTrue(check.done());
    }

    @Test
    void aJobWaitingIsQueuedAndNotStarted() {
        ForgejoCheck check = ForgejoCheck.of(List.of(posted(1, "ci / test (push)", "pending", "Waiting to run", 0)))
                .single();
        assertTrue(check.queued());
        assertEquals(Option.none(), check.started());
        assertEquals(Option.none(), check.ended());
    }

    @Test
    void aJobRunAgainIsTimedFromItsNewAttempt() {
        ForgejoCheck check = ForgejoCheck.of(List.of(
                        posted(1, "ci / test (push)", "pending", "Has started running", 0),
                        posted(2, "ci / test (push)", "failure", "Failing after 10s", 10),
                        posted(3, "ci / test (push)", "pending", "Waiting to run", 100),
                        posted(4, "ci / test (push)", "pending", "Has started running", 110)))
                .single();
        assertEquals(T0.plusSeconds(100), check.created());
        assertEquals(Option.of(T0.plusSeconds(110)), check.started());
        assertFalse(check.done());
        assertFalse(check.queued());
    }

    @Test
    void aStatusOfAnotherCiIsTimedFromWhenItWasPosted() {
        ForgejoCheck check = ForgejoCheck.of(List.of(new ForgejoCheck.Posted(
                        1, "woodpecker", "pending", "running", Option.of("https://ci.example.net/1"), T0)))
                .single();
        assertEquals(Option.of(T0), check.started());
        assertEquals(Option.none(), check.job());
    }

    @Test
    void theChecksAreInTheOrderTheirContextsWereFirstPosted() {
        List<ForgejoCheck> checks = ForgejoCheck.of(List.of(
                posted(2, "b", "pending", "Waiting to run", 0),
                posted(1, "a", "pending", "Waiting to run", 0),
                posted(3, "a", "success", "Successful in 1s", 1)));
        assertEquals(List.of("a", "b"), checks.map(ForgejoCheck::name));
    }

    @Test
    void theJobIsTheRunAndPlaceItsLinkNames() {
        ForgejoCheck check = ForgejoCheck.of(List.of(new ForgejoCheck.Posted(
                        1,
                        "ci / test (push)",
                        "success",
                        "Successful in 1s",
                        Option.of("https://code.example.net/cvf/nix/actions/runs/12/jobs/3/attempt/2"),
                        T0)))
                .single();
        assertEquals(Option.of(new ForgejoCheck.ActionsJob(12, 3)), check.job());
    }

    @Test
    void aSkippedJobSaysSo() {
        assertTrue(ForgejoCheck.of(List.of(posted(1, "x", "success", "Has been skipped", 0)))
                .single()
                .skipped());
    }
}
