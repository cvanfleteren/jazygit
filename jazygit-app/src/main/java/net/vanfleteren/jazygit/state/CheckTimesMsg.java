package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.RecentChecks;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;

/**
 * Learning how long the checks of a repository take (ADR 30).
 */
public sealed interface CheckTimesMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CheckTimesUpdate.update(model, this);
    }

    /**
     * The history kept of {@code repository} is {@code history}, with what was just learned. When {@code topUp},
     * the checks that ran lately are to be read from GitHub, if it is old.
     */
    record Recorded(String repository, CheckHistory history, boolean topUp) implements CheckTimesMsg {}

    /**
     * {@code samples} of {@code repository} could not be kept; they are still learned, for this session.
     */
    record NotRecorded(String repository, List<CheckSample> samples, boolean topUp) implements CheckTimesMsg {}

    /**
     * The checks that ran lately on {@code repository} were read from GitHub.
     */
    record ToppedUp(String repository, RecentChecks recent) implements CheckTimesMsg {}

    /**
     * Reading the checks that ran lately on {@code repository} from GitHub failed.
     */
    record TopUpFailed(String repository) implements CheckTimesMsg {}
}
