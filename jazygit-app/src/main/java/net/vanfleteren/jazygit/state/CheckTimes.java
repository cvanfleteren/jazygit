package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.gh.timing.Eta;

/**
 * How long the checks of the repository took, as far as it was learned (ADR 32).
 *
 * @param repository the repository, as {@code owner/name}
 * @param history    how long its checks took
 * @param toppingUp  whether the checks that ran lately are being read from GitHub
 * @param toppedUp   when reading them from GitHub was last started, here or by another jazygit; none when never
 */
public record CheckTimes(String repository, CheckHistory history, boolean toppingUp, Option<Instant> toppedUp) {

    /**
     * What was learned of {@code repository} so far: {@code history}, last topped up from GitHub at
     * {@code toppedUp}.
     */
    public static CheckTimes of(String repository, CheckHistory history, Option<Instant> toppedUp) {
        return new CheckTimes(repository, history, false, toppedUp);
    }

    /**
     * This, with {@code history}, and topped up last at the later of when it was here and {@code toppedUp}: what is
     * in memory is not undone by a file that could not be written.
     */
    public CheckTimes withKept(CheckHistory history, Option<Instant> toppedUp) {
        return new CheckTimes(
                repository,
                history,
                toppingUp,
                List.of(this.toppedUp, toppedUp).flatMap(at -> at).max());
    }

    /**
     * This, reading the checks that ran lately from GitHub, which started at {@code now}.
     */
    public CheckTimes withTopUp(Instant now) {
        return new CheckTimes(repository, history, true, Option.of(now));
    }

    /**
     * This, done reading the checks that ran lately from GitHub, whether that worked or not.
     */
    public CheckTimes withTopUpDone() {
        return new CheckTimes(repository, history, false, toppedUp);
    }

    /**
     * This, not reading the checks that ran lately from GitHub after all, as another jazygit did at {@code at}.
     */
    public CheckTimes withTopUpSkipped(Instant at) {
        return new CheckTimes(repository, history, false, Option.of(at));
    }

    /**
     * Whether to read the checks that ran lately from GitHub at {@code now}: they are not being read, and were not
     * read, or tried to be, lately; not the age of the history, so a quiet repository, an empty result and a
     * failing {@code gh} all wait the day out.
     */
    public boolean topUpDue(Instant now) {
        return !toppingUp && !toppedUp.exists(at -> toppedUpLately(at, now));
    }

    /**
     * Whether reading the checks that ran lately from GitHub at {@code at} is lately at {@code now}: in the last
     * {@link CheckTimings#TOP_UP_AFTER}.
     */
    public static boolean toppedUpLately(Instant at, Instant now) {
        return !at.plus(CheckTimings.TOP_UP_AFTER).isBefore(now);
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
