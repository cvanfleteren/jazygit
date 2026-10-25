package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.gh.timing.Eta;

/**
 * How long the checks of the repository took, as far as it was learned (ADR 30).
 *
 * @param repository the repository, as {@code owner/name}
 * @param history    how long its checks took
 * @param toppingUp  whether the checks that ran lately are being read from GitHub
 * @param toppedUp   when they were last read from GitHub, or that failed; none when not in this session
 */
public record CheckTimes(String repository, CheckHistory history, boolean toppingUp, Option<Instant> toppedUp) {

    /**
     * What was learned of {@code repository} so far: {@code history}, not read from GitHub yet.
     */
    public static CheckTimes of(String repository, CheckHistory history) {
        return new CheckTimes(repository, history, false, Option.none());
    }

    public CheckTimes withHistory(CheckHistory history) {
        return new CheckTimes(repository, history, toppingUp, toppedUp);
    }

    /**
     * This, reading the checks that ran lately from GitHub.
     */
    public CheckTimes withTopUp() {
        return new CheckTimes(repository, history, true, toppedUp);
    }

    /**
     * This, done reading the checks that ran lately from GitHub at {@code now}, whether that worked or not.
     */
    public CheckTimes withTopUpDone(Instant now) {
        return new CheckTimes(repository, history, false, Option.of(now));
    }

    /**
     * Whether to read the checks that ran lately from GitHub at {@code now}: the history is empty or old, and they
     * are not being read, nor were read in the last {@link CheckTimings#TOP_UP_RETRY}.
     */
    public boolean topUpDue(Instant now) {
        return !toppingUp
                && history.stale(now)
                && toppedUp.forAll(at -> at.plus(CheckTimings.TOP_UP_RETRY).isBefore(now));
    }

    /**
     * When {@code check}, one of the checks {@code all} of a commit, is expected to be done.
     */
    public Eta eta(Check check, List<Check> all) {
        return Eta.of(check, all, history);
    }

    /**
     * When the checks {@code all} of a commit are expected to be done together.
     */
    public Eta eta(List<Check> all) {
        return Eta.of(all, history);
    }
}
