package net.vanfleteren.jazygit.gh.timing;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * One run of a check that passed, as the time checks take is learned from (ADR 32).
 *
 * @param check    the check, as {@link #key} names it
 * @param id       the id of the check run, so that it is learned from once
 * @param duration how long it ran
 * @param finished how long after its workflow run was created it was done
 * @param event    what started its workflow run, like {@code push} or {@code pull_request}
 * @param at       when it was done
 */
public record CheckSample(String check, long id, Duration duration, Duration finished, String event, Instant at) {

    /**
     * The name a check is known by in the history: its workflow and its own name, as one workflow can have a check
     * named as one of another.
     */
    public static String key(String workflow, String name) {
        return workflow + "/" + name;
    }

    /**
     * The name {@code check} is known by in the history; none for one that is not timed.
     */
    public static Option<String> key(Check check) {
        return check.run().map(run -> key(run.workflow(), check.name()));
    }

    /**
     * What {@code check} teaches: only a check run that passed, in the first attempt of its workflow run, as a re-run
     * is timed from when the first attempt was created.
     */
    public static Option<CheckSample> of(Check check) {
        return check.run()
                .filter(run -> run.succeeded() && run.runAttempt() == 1)
                .flatMap(run -> run.startedAt()
                        .flatMap(started ->
                                run.completedAt().map(completed -> sample(check.name(), run, started, completed))));
    }

    private static CheckSample sample(String name, CheckRun run, Instant started, Instant completed) {
        return new CheckSample(
                key(run.workflow(), name),
                run.id(),
                Duration.between(started, completed),
                Duration.between(run.runCreatedAt(), completed),
                run.event(),
                completed);
    }
}
