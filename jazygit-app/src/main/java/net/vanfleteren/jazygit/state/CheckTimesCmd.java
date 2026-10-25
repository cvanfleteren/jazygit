package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Run;

/**
 * Learning how long the checks of a repository take (ADR 30). They run in the background, as nobody waits for them.
 */
public sealed interface CheckTimesCmd extends Cmd {

    @Override
    default boolean background() {
        return true;
    }

    /**
     * Adds {@code samples} to the history kept of {@code repository} at {@code now}, and reads it; with none, only
     * reads it. When {@code topUp}, the checks that ran lately are read from GitHub next, if the history is old.
     */
    record RecordCheckTimes(String repository, List<CheckSample> samples, Instant now, boolean topUp)
            implements CheckTimesCmd {

        @Override
        public Msg run(Env env) {
            return samples.isEmpty()
                    ? new CheckTimesMsg.Recorded(
                            repository,
                            env.checkTimings()
                                    .load(repository)
                                    .map(RecordCheckTimes::history)
                                    .getOrElse(CheckHistory.EMPTY),
                            topUp)
                    : env.checkTimings()
                            .change(repository, kept -> runs(history(kept).with(samples, now)))
                            .fold(
                                    failed -> new CheckTimesMsg.NotRecorded(repository, samples, topUp),
                                    kept -> new CheckTimesMsg.Recorded(repository, history(kept), topUp));
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
     * Reads the checks that ran lately on {@code repository} from GitHub.
     */
    record TopUpCheckTimes(String repository) implements CheckTimesCmd {

        @Override
        public Msg run(Env env) {
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
