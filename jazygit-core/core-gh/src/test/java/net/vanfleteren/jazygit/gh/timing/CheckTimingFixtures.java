package net.vanfleteren.jazygit.gh.timing;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * Checks and runs of them, timed from {@link #T0}, for tests.
 */
public final class CheckTimingFixtures {

    public static final Instant T0 = Instant.parse("2026-10-25T10:00:00Z");

    private CheckTimingFixtures() {}

    public static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    /**
     * A check of workflow {@code Build}, of a first attempt created at {@link #T0}: queued while it has no start,
     * running while it has no end, and passed once it has.
     */
    public static Check check(String name, Option<Integer> started, Option<Integer> completed) {
        return check(name, 1, 1, started, completed);
    }

    public static Check check(
            String name, long runId, int attempt, Option<Integer> started, Option<Integer> completed) {
        return check("Build", "pull_request", name, runId, attempt, started, completed);
    }

    /**
     * A check of {@code workflow}, of a run started by {@code event}.
     */
    public static Check check(
            String workflow,
            String event,
            String name,
            long runId,
            int attempt,
            Option<Integer> started,
            Option<Integer> completed) {
        return new Check(
                name,
                completed.isDefined() ? Checks.PASSING : Checks.PENDING,
                Option.of(new CheckRun(
                        name.hashCode() * 31L + runId,
                        workflow,
                        completed.isDefined() ? "SUCCESS" : "",
                        started.map(CheckTimingFixtures::at),
                        completed.map(CheckTimingFixtures::at),
                        runId,
                        T0,
                        attempt,
                        event)));
    }

    public static Check queued(String name) {
        return check(name, Option.none(), Option.none());
    }

    public static Check running(String name, int started) {
        return check(name, Option.of(started), Option.none());
    }

    /**
     * Runs of check {@code Build/<name>}, each done at {@code at}, that ran for {@code durations} and were done
     * {@code finished} after their run was created.
     */
    public static List<CheckSample> samples(String name, int[] durations, int[] finished, Instant at) {
        return samples("Build", "pull_request", name, durations, finished, at);
    }

    /**
     * Runs of check {@code <workflow>/<name>}, of runs started by {@code event}.
     */
    public static List<CheckSample> samples(
            String workflow, String event, String name, int[] durations, int[] finished, Instant at) {
        return List.range(0, durations.length)
                .map(i -> new CheckSample(
                        CheckSample.key(workflow, name),
                        CheckSample.key(workflow, name).hashCode() * 1000L + i,
                        Duration.ofSeconds(durations[i]),
                        Duration.ofSeconds(finished[i]),
                        event,
                        at.minusSeconds(i)));
    }
}
