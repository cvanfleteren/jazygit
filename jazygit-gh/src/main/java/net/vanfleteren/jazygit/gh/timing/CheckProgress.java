package net.vanfleteren.jazygit.gh.timing;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * How far the checks of a commit, or one of them, are, from 0 to 1 (ADR 89): to {@link #EXPECTED} as the time goes by
 * towards when they are expected to be done, then on towards {@link #EXPECTED} plus {@link #LATE} while they take
 * longer, never reaching it; 1 only once they are done. So it is never full while one still runs.
 */
public final class CheckProgress {

    /**
     * How far they are when they are expected to be done.
     */
    public static final double EXPECTED = 0.95;

    // How much further they go, at most, while they take longer than expected.
    private static final double LATE = 0.04;

    private CheckProgress() {}

    /**
     * How far the checks {@code all} of a commit are at {@code now}, expected to be done together at {@code eta}:
     * from the start of those that are not done (ADR 89) as the time goes by, so a workflow run again starts from 0.
     * While that is not known, or they have no start, as only commit statuses do, as far as the share of them that is
     * done.
     */
    public static double of(List<Check> all, Eta eta, Instant now) {
        return switch (eta) {
            case Eta.Done done -> 1;
            case Eta.Expected(Instant at) ->
                CheckSpan.current(all).map(start -> towards(start, at, now)).getOrElse(() -> counted(all));
            case Eta.Unknown unknown -> counted(all);
        };
    }

    /**
     * How far {@code check} is at {@code now}, expected to be done at {@code eta}: 1 once it is done, 0 while it is
     * queued, and from its own start while it runs; nothing while that or when it is expected is not known.
     */
    public static Option<Double> of(Check check, Eta eta, Instant now) {
        if (check.done()) {
            return Option.of(1.0);
        }
        if (check.queued()) {
            return Option.of(0.0);
        }
        return eta instanceof Eta.Expected(Instant at)
                ? check.run().flatMap(CheckRun::startedAt).map(started -> towards(started, at, now))
                : Option.none();
    }

    /**
     * How far something that started at {@code start} and is expected to be done at {@code at} is at {@code now}.
     * Past {@code at}, it goes on as slower as it is later, in proportion to how long it was expected to take.
     */
    private static double towards(Instant start, Instant at, Instant now) {
        double expected = Math.max(1, Duration.between(start, at).toMillis());
        double elapsed = Math.max(0, Duration.between(start, now).toMillis());
        return elapsed <= expected
                ? EXPECTED * elapsed / expected
                : EXPECTED + LATE * (1 - Math.exp(-(elapsed - expected) / expected));
    }

    private static double counted(List<Check> all) {
        return all.isEmpty() ? 0 : EXPECTED * all.count(Check::done) / all.size();
    }
}
