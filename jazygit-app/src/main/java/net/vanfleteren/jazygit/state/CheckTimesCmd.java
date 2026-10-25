package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Kept;
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Run;

/**
 * Learning how long the checks of a repository take (ADR 32). They run in the background, as nobody waits for them.
 */
public sealed interface CheckTimesCmd extends Cmd {

    @Override
    default boolean background() {
        return true;
    }

    /**
     * Adds {@code samples} to the history kept of {@code repository} at {@code now}, and reads it; with none, only
     * reads it. When {@code topUp}, the checks that ran lately are read from GitHub next, if they were not lately.
     */
    record RecordCheckTimes(String repository, List<CheckSample> samples, Instant now, boolean topUp)
            implements CheckTimesCmd {

        @Override
        public Msg run(Env env) {
            return samples.isEmpty()
                    ? recorded(env.checkTimings().load(repository).getOrElse(Kept.EMPTY))
                    : env.checkTimings()
                            .change(
                                    repository,
                                    kept -> kept.withRuns(
                                            runs(history(kept.runs()).with(samples, now))))
                            .fold(failed -> new CheckTimesMsg.NotRecorded(repository, samples, topUp), this::recorded);
        }

        private Msg recorded(Kept kept) {
            return new CheckTimesMsg.Recorded(repository, history(kept.runs()), kept.toppedUp(), topUp);
        }

        @Override
        public Msg failed(Failure failure) {
            return new CheckTimesMsg.NotRecorded(repository, samples, topUp);
        }

        private static CheckHistory history(List<Run> runs) {
            return new CheckHistory(runs.map(run -> new CheckSample(
                            run.check(),
                            run.id(),
                            Duration.ofSeconds(run.duration()),
                            Duration.ofSeconds(run.finished()),
                            run.event(),
                            run.at()))
                    .groupBy(CheckSample::check)
                    .mapValues(ofCheck -> ofCheck.sortBy(CheckSample::at).reverse()));
        }

        private static List<Run> runs(CheckHistory history) {
            return history.samples()
                    .values()
                    .toList()
                    .flatMap(ofCheck -> ofCheck)
                    .map(sample -> new Run(
                            sample.check(),
                            sample.id(),
                            sample.duration().toSeconds(),
                            sample.finished().toSeconds(),
                            sample.event(),
                            sample.at()));
        }
    }

    /**
     * Reads the checks that ran lately on {@code repository} from GitHub, keeping first that this was tried at
     * {@code now}, so that neither this jazygit nor another tries again that day, whether it works or not. When
     * another jazygit kept that it tried in the last day, it is not tried again; that is decided under the file's
     * lock, so two jazygits do not both try.
     */
    record TopUpCheckTimes(String repository, Instant now) implements CheckTimesCmd {

        @Override
        public Msg run(Env env) {
            // Kept or not, the gate in memory holds this jazygit to one try a day.
            return env.checkTimings()
                    .change(
                            repository,
                            kept -> kept.toppedUp().exists(at -> CheckTimes.toppedUpLately(at, now))
                                    ? kept
                                    : kept.withToppedUp(now))
                    .fold(
                            failed -> topUp(env),
                            kept -> kept.toppedUp()
                                    .filter(at -> !at.equals(now))
                                    .<Msg>map(at -> new CheckTimesMsg.TopUpSkipped(repository, at))
                                    .getOrElse(() -> topUp(env)));
        }

        private Msg topUp(Env env) {
            return env.gitHub()
                    .recentChecks()
                    .fold(this::failedWith, recent -> new CheckTimesMsg.ToppedUp(repository, recent));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CheckTimesMsg.TopUpFailed(repository);
        }
    }
}
