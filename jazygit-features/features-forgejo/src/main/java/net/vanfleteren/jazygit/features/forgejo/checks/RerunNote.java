package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;

/**
 * What the checks popup says of running failed checks again (ADR 135, 195).
 */
public sealed interface RerunNote {

    /**
     * How long the checks are read every 10 s after they were asked to run again, until one of them shows not done.
     */
    Duration AWAITED = Duration.ofMinutes(1);

    /**
     * {@code r} found nothing to run again, for {@code why}.
     */
    record Nothing(RerunPlan.Why why) implements RerunNote {}

    /**
     * The failed jobs of the runs {@code runs} were asked to run again at {@code at}.
     *
     * @param outcomes how that went for each run; none while Forgejo is still being asked
     * @param seen     whether a check of those runs was read not done since, so the run is seen to run again
     */
    record Asked(Instant at, List<Long> runs, Option<List<RerunCmd.Outcome>> outcomes, boolean seen)
            implements RerunNote {

        /**
         * Asked at {@code at}, before Forgejo answered.
         */
        public static Asked at(Instant at, List<Long> runs) {
            return new Asked(at, runs, Option.none(), false);
        }

        /**
         * This, once Forgejo answered with {@code outcomes}.
         */
        public Asked answered(List<RerunCmd.Outcome> outcomes) {
            return new Asked(at, runs, Option.of(outcomes), seen);
        }

        /**
         * This, once the checks {@code read} were read: seen when one of the runs run again is not done in them.
         */
        public Asked reading(List<ForgejoCheck> read) {
            return new Asked(
                    at,
                    runs,
                    outcomes,
                    seen || read.exists(check -> !check.done() && check.job().exists(job -> runs.contains(job.run()))));
        }

        /**
         * Whether the runs are still waited for to show running again at {@code now}: not seen yet, for no longer than
         * {@link #AWAITED}, and not when Forgejo refused to run any of them again, as none will then.
         */
        public boolean awaited(Instant now) {
            return !seen
                    && now.isBefore(at.plus(AWAITED))
                    && outcomes.forAll(
                            all -> all.exists(outcome -> outcome.refused().isEmpty()));
        }

        /**
         * Whether the runs are still being run again at {@code now}: Forgejo has not answered yet, or they are still
         * waited for to show running.
         */
        public boolean pending(Instant now) {
            return outcomes.isEmpty() || awaited(now);
        }

        /**
         * The runs Forgejo refused to run again, with why; none while it was not answered.
         */
        public List<RerunCmd.Outcome> refused() {
            return outcomes.getOrElse(List.empty())
                    .filter(outcome -> outcome.refused().isDefined());
        }
    }
}
