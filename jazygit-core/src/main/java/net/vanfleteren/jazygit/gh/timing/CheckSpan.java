package net.vanfleteren.jazygit.gh.timing;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * When the checks of a commit started and, once they are all done, ended (ADR 37).
 *
 * @param start when the first of their workflow runs started
 * @param end   when the last of them was done; none while one is not
 */
public record CheckSpan(Instant start, Option<Instant> end) {

    /**
     * When the checks {@code all} of a commit started, from the start of their workflow runs, and ended; none when
     * none of them is timed, as a commit status is not.
     */
    public static Option<CheckSpan> of(List<Check> all) {
        Option<Instant> end = all.forAll(Check::done)
                ? all.flatMap(Check::run).flatMap(CheckRun::completedAt).max()
                : Option.none();
        return all.flatMap(Check::run).flatMap(run -> runStart(run, all)).min().map(start -> new CheckSpan(start, end));
    }

    /**
     * When the workflow run of {@code run} started: when it was created, for its first attempt; for a re-run, when
     * the first of its checks among {@code all} started, as GitHub does not say when an attempt started.
     */
    static Option<Instant> runStart(CheckRun run, List<Check> all) {
        return run.runAttempt() == 1
                ? Option.of(run.runCreatedAt())
                : all.flatMap(Check::run)
                        .filter(other -> other.runId() == run.runId())
                        .flatMap(CheckRun::startedAt)
                        .min();
    }

    /**
     * How long they took, from their start to their end, or to {@code now} while one is not done.
     */
    public Duration took(Instant now) {
        return Duration.between(start, end.getOrElse(now));
    }
}
