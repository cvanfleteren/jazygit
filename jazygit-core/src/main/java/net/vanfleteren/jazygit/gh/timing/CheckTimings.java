package net.vanfleteren.jazygit.gh.timing;

import java.time.Duration;

/**
 * How the time checks take is learned and estimated (ADR 32), in one place, so they can become settings.
 */
public final class CheckTimings {

    /**
     * How many of its runs are kept of a check, the newest.
     */
    public static final int SAMPLES = 20;

    /**
     * How many runs of a check it takes to estimate it.
     */
    public static final int MIN_SAMPLES = 3;

    /**
     * How long after the recent checks of a repository were read from GitHub they are read again at the soonest:
     * also after that failed, or found nothing newer.
     */
    public static final Duration TOP_UP_AFTER = Duration.ofDays(1);

    /**
     * How many merged pull requests, and commits of the default branch, the recent checks are read of.
     */
    public static final int TOP_UP_COMMITS = 20;

    /**
     * How long a check is kept without a new run of it.
     */
    public static final Duration FORGET_AFTER = Duration.ofDays(90);

    /**
     * How long past its estimate a check is before it is overdue: the checks are read every minute, so one that is
     * done can look running for that long.
     */
    public static final Duration GRACE = Duration.ofMinutes(1);

    /**
     * How often a dialog that lists checks is told to read them again, which it does while one is not done (ADR 34).
     */
    public static final Duration READ_WHILE_PENDING = Duration.ofSeconds(10);

    /**
     * How long after its last read a dialog that lists checks reads them again when they are all done, for a re-run,
     * a push, or a review: a minute, less half a tick, as the tick a minute on can come a few ms short of it (ADR 34).
     */
    public static final Duration READ_WHEN_DONE = Duration.ofMinutes(1).minus(READ_WHILE_PENDING.dividedBy(2));

    /**
     * How many branches whose checks are pending have their checks read with their timing at once.
     */
    public static final int PENDING_BRANCHES = 10;

    private CheckTimings() {}
}
