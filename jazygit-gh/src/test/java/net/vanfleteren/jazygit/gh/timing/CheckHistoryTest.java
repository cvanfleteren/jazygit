package net.vanfleteren.jazygit.gh.timing;

import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.T0;
import static net.vanfleteren.jazygit.gh.timing.CheckTimingFixtures.samples;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

class CheckHistoryTest {

    @Test
    void theMedianOfTheRunsOfACheck() {
        CheckHistory history =
                CheckHistory.EMPTY.with(samples("verify", new int[] {100, 60, 80}, new int[] {110, 70, 300}, T0), T0);

        assertEquals(Option.of(Duration.ofSeconds(80)), history.duration("Build/verify"));
        assertEquals(Option.of(Duration.ofSeconds(110)), history.finished("Build/verify"));
    }

    @Test
    void theMedianOfAnEvenNumberOfRunsIsBetweenTheMiddleTwo() {
        CheckHistory history =
                CheckHistory.EMPTY.with(samples("verify", new int[] {100, 60, 80, 90}, new int[] {1, 2, 3, 4}, T0), T0);

        assertEquals(Option.of(Duration.ofSeconds(85)), history.duration("Build/verify"));
    }

    @Test
    void underThreeRunsACheckIsNotKnown() {
        CheckHistory history =
                CheckHistory.EMPTY.with(samples("verify", new int[] {100, 60}, new int[] {110, 70}, T0), T0);

        assertEquals(Option.none(), history.duration("Build/verify"));
        assertEquals(Option.none(), history.finished("Build/verify"));
        assertEquals(Option.none(), history.duration("Build/other"));
    }

    @Test
    void theChecksOfAWorkflowWithEnoughRunsOfAnEvent() {
        CheckHistory history = CheckHistory.EMPTY
                .with(samples("verify", new int[] {1, 2, 3}, new int[] {1, 2, 3}, T0), T0)
                .with(samples("package", new int[] {1, 2}, new int[] {1, 2}, T0), T0)
                .with(samples("Build", "push", "snapshot", new int[] {1, 2, 3}, new int[] {1, 2, 3}, T0), T0)
                .with(samples("Builder", "pull_request", "z", new int[] {1, 2, 3}, new int[] {1, 2, 3}, T0), T0);

        assertEquals(List.of("Build/verify"), history.checksOf("Build", "pull_request"));
        assertEquals(List.of("Build/snapshot"), history.checksOf("Build", "push"));
    }

    @Test
    void aRunIsLearnedOnce() {
        List<CheckSample> runs = samples("verify", new int[] {100, 60}, new int[] {110, 70}, T0);

        CheckHistory history = CheckHistory.EMPTY.with(runs, T0).with(runs, T0);

        assertEquals(2, history.samples().get("Build/verify").get().size());
        assertEquals(List.empty(), history.unknown(runs));
        assertEquals(runs.take(1), CheckHistory.EMPTY.with(runs.drop(1), T0).unknown(runs));
    }

    @Test
    void aCheckKeepsItsNewestRuns() {
        int[] durations = new int[25];
        java.util.Arrays.setAll(durations, i -> i);

        CheckHistory history = CheckHistory.EMPTY.with(samples("verify", durations, durations, T0), T0);

        List<CheckSample> kept = history.samples().get("Build/verify").get();
        assertEquals(CheckTimings.SAMPLES, kept.size());
        assertEquals(T0, kept.head().at());
        assertEquals(Duration.ofSeconds(19), kept.last().duration());
    }

    @Test
    void aCheckNotRunForLongIsForgotten() {
        Instant old = T0.minus(CheckTimings.FORGET_AFTER).minusSeconds(60);
        CheckHistory history = CheckHistory.EMPTY
                .with(samples("old", new int[] {1, 2, 3}, new int[] {1, 2, 3}, old), old)
                .with(samples("verify", new int[] {1}, new int[] {1}, T0), T0);

        assertEquals(List.of("Build/verify"), history.samples().keySet().toList());
    }

    @Test
    void aSampleIsACheckRunThatPassedInTheFirstAttempt() {
        CheckRun run = new CheckRun(
                7, "Build", "SUCCESS", Option.of(T0.plusSeconds(62)), Option.of(T0.plusSeconds(254)), 3, T0, 1, "push");

        assertEquals(
                Option.of(new CheckSample(
                        "Build/acceptance",
                        7,
                        Duration.ofSeconds(192),
                        Duration.ofSeconds(254),
                        "push",
                        T0.plusSeconds(254))),
                CheckSample.of(new Check("acceptance", Checks.PASSING, Option.of(run))));
        assertEquals(
                Option.none(),
                CheckSample.of(new Check(
                        "acceptance",
                        Checks.FAILING,
                        Option.of(new CheckRun(
                                7, "Build", "FAILURE", run.startedAt(), run.completedAt(), 3, T0, 1, "push")))));
        assertEquals(
                Option.none(),
                CheckSample.of(new Check(
                        "acceptance",
                        Checks.PASSING,
                        Option.of(new CheckRun(
                                7, "Build", "SUCCESS", run.startedAt(), run.completedAt(), 3, T0, 2, "push")))));
        assertEquals(Option.none(), CheckSample.of(new Check("ci/status", Checks.PASSING)));
    }
}
