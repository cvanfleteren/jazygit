package net.vanfleteren.jazygit.gh.timing;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * When a check, or the checks of a commit together, are expected to be done (ADR 32).
 */
public sealed interface Eta {

    /**
     * When {@code check}, one of the checks {@code all} of a commit, is expected to be done, as {@code history} tells.
     * A check is timed from when its workflow run started, which covers the checks that wait for others: queued, it
     * is expected when it was usually done after the start of its run; running, at the later of that and its own
     * start plus how long it usually runs.
     */
    static Eta of(Check check, List<Check> all, CheckHistory history) {
        if (check.done()) {
            return new Done();
        }
        Option<Instant> fromRun = check.run()
                .flatMap(run -> runStart(run, all))
                .flatMap(start ->
                        CheckSample.key(check).flatMap(history::finished).map(start::plus));
        Option<Instant> fromStart = check.run()
                .flatMap(CheckRun::startedAt)
                .flatMap(started ->
                        CheckSample.key(check).flatMap(history::duration).map(started::plus));
        Option<Instant> expected = check.queued()
                ? fromRun
                : fromStart.map(
                        start -> fromRun.filter(run -> run.isAfter(start)).getOrElse(start));
        return expected.<Eta>map(Expected::new).getOrElse(Unknown::new);
    }

    /**
     * When the checks {@code all} of a commit are expected to be done together: done when they all are, not known
     * when one that is not done is not known, as they are not done before it is. A workflow run with a check that is
     * not done also waits for the checks it has that GitHub did not make yet (ADR 36).
     */
    static Eta of(List<Check> all, CheckHistory history) {
        List<Eta> pending = all.filter(check -> !check.done()).map(check -> of(check, all, history));
        if (pending.isEmpty()) {
            return new Done();
        }
        List<Instant> expected =
                pending.flatMap(eta -> eta instanceof Expected(Instant at) ? List.of(at) : List.empty());
        return expected.size() < pending.size()
                ? new Unknown()
                : new Expected(expected.appendAll(missing(all, history)).max().get());
    }

    /**
     * When the checks that the workflow runs among {@code all} have, as {@code history} tells, but that their run does
     * not show among {@code all} yet, are expected done: as a queued check of their run, while the run has a check that
     * is not done. A job that needs others is made only once those are done; when none of its run is left, it is not
     * coming.
     */
    private static List<Instant> missing(List<Check> all, CheckHistory history) {
        return all.filter(check -> !check.done())
                .flatMap(Check::run)
                .distinctBy(CheckRun::runId)
                .flatMap(run -> {
                    List<String> shown = all.filter(check -> check.run().exists(other -> other.runId() == run.runId()))
                            .flatMap(CheckSample::key);
                    return runStart(run, all)
                            .toList()
                            .flatMap(start -> history.checksOf(run.workflow(), run.event())
                                    .filter(check -> !shown.contains(check))
                                    .flatMap(history::finished)
                                    .map(start::plus));
                });
    }

    /**
     * When the workflow run of {@code run} started: when it was created, for its first attempt; for a re-run, when
     * the first of its checks among {@code all} started, as GitHub does not say when an attempt started.
     */
    private static Option<Instant> runStart(CheckRun run, List<Check> all) {
        return run.runAttempt() == 1
                ? Option.of(run.runCreatedAt())
                : all.flatMap(Check::run)
                        .filter(other -> other.runId() == run.runId())
                        .flatMap(CheckRun::startedAt)
                        .min();
    }

    /**
     * It is done, or they all are.
     */
    record Done() implements Eta {}

    /**
     * There is too little known to tell.
     */
    record Unknown() implements Eta {}

    /**
     * Expected to be done {@code at}.
     */
    record Expected(Instant at) implements Eta {

        /**
         * How long it is still expected to take at {@code now}; negative once that is past.
         */
        public Duration left(Instant now) {
            return Duration.between(now, at);
        }

        /**
         * Whether it takes longer than usual at {@code now}: more than {@link CheckTimings#GRACE} past what was
         * expected, as a check that is done looks running until the checks are read again.
         */
        public boolean overdue(Instant now) {
            return now.isAfter(at.plus(CheckTimings.GRACE));
        }
    }
}
