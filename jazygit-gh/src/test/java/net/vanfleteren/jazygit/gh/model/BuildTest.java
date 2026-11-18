package net.vanfleteren.jazygit.gh.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * How a build ended, how long it took (ADR 131) and how long it queued (ADR 133).
 */
class BuildTest {

    private static final Instant CREATED = Instant.parse("2026-11-14T23:00:00Z");

    private static Build.Job job(String conclusion, long from, long to) {
        return new Build.Job(
                "job", conclusion, Option.of(CREATED.plusSeconds(from)), Option.of(CREATED.plusSeconds(to)));
    }

    private static Build build(int attempt, Build.Job... jobs) {
        return new Build("Build", "push", 1, attempt, "abc", "Title", CREATED, List.of(jobs));
    }

    @Test
    void aBuildWhoseJobsPassedOrWereSkippedPassed() {
        assertEquals(
                Build.Outcome.PASSED,
                build(1, job("SUCCESS", 2, 60), job("SKIPPED", 61, 61)).outcome());
    }

    @Test
    void oneFailedJobFailsIt() {
        assertEquals(
                Build.Outcome.FAILED,
                build(1, job("CANCELLED", 2, 60), job("TIMED_OUT", 2, 90)).outcome());
    }

    @Test
    void oneCancelledJobCancelsIt() {
        assertEquals(
                Build.Outcome.CANCELLED,
                build(1, job("SUCCESS", 2, 60), job("CANCELLED", 2, 30)).outcome());
    }

    @Test
    void oneRunningJobKeepsItRunningWithoutADuration() {
        Build running = build(1, job("FAILURE", 2, 60), new Build.Job("job", "", Option.of(CREATED), Option.none()));

        assertEquals(Build.Outcome.RUNNING, running.outcome());
        assertEquals(Option.none(), running.duration());
    }

    @Test
    void aBuildWithoutAJobYetWaitsToStart() {
        Build held = build(1);

        assertEquals(Build.Outcome.RUNNING, held.outcome());
        assertEquals(Option.none(), held.duration());
        assertEquals(Option.none(), held.queued());
    }

    @Test
    void itTookFromItsRunsStartToItsLastJobsEnd() {
        assertEquals(
                Option.of(Duration.ofSeconds(250)),
                build(1, job("SUCCESS", 2, 60), job("SUCCESS", 60, 250)).duration());
    }

    @Test
    void aReRunTookFromItsFirstJobsStart() {
        assertEquals(
                Option.of(Duration.ofSeconds(100)),
                build(2, job("SUCCESS", 3600, 3700), job("SUCCESS", 3650, 3660)).duration());
    }

    @Test
    void itRanTheJobsThatWereNotSkipped() {
        assertEquals(
                1, build(1, job("SUCCESS", 2, 60), job("SKIPPED", 61, 61)).ran().size());
    }

    @Test
    void itQueuedFromItsRunsStartToItsFirstJobsStart() {
        assertEquals(
                Option.of(Duration.ofSeconds(12)),
                build(1, job("SUCCESS", 30, 60), job("SUCCESS", 12, 40)).queued());
    }

    @Test
    void aReRunSaysNothingOfItsQueue() {
        // Its run keeps the first attempt's start, so the wait until its first job would count the first attempt.
        assertEquals(Option.none(), build(2, job("SUCCESS", 600, 660)).queued());
    }
}
