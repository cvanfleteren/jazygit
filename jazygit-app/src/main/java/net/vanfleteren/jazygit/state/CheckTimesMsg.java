package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.RecentChecks;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;

/**
 * Learning how long the checks of a repository take (ADR 32).
 */
public sealed interface CheckTimesMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CheckTimesUpdate.update(model, this);
    }

    /**
     * The history kept of {@code repository} is {@code history}, with what was just learned, and its checks were
     * last topped up from GitHub at {@code toppedUp}. When {@code topUp}, the checks that ran lately are to be read
     * from GitHub, if they were not lately.
     */
    record Recorded(String repository, CheckHistory history, Option<Instant> toppedUp, boolean topUp)
            implements CheckTimesMsg {}

    /**
     * {@code samples} of {@code repository} could not be kept; they are still learned, for this session.
     */
    record NotRecorded(String repository, List<CheckSample> samples, boolean topUp) implements CheckTimesMsg {}

    /**
     * The checks that ran lately on {@code repository} were read from GitHub.
     */
    record ToppedUp(String repository, RecentChecks recent) implements CheckTimesMsg {}

    /**
     * The checks that ran lately on {@code repository} were not read from GitHub, as another jazygit did so, or
     * tried, at {@code toppedUp}, in the last day.
     */
    record TopUpSkipped(String repository, Instant toppedUp) implements CheckTimesMsg {}

    /**
     * Reading the checks that ran lately on {@code repository} from GitHub failed.
     */
    record TopUpFailed(String repository) implements CheckTimesMsg {}
}
