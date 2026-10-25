package net.vanfleteren.jazygit.gh.timing;

import java.time.Duration;

/**
 * How the time checks take is learned and estimated (ADR 30), in one place, so they can become settings.
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
     * How old the newest run kept of a repository may get before its recent checks are read from GitHub.
     */
    public static final Duration TOP_UP_AFTER = Duration.ofDays(1);

    /**
     * How long after reading the recent checks from GitHub they are read again at the soonest: also after that
     * failed, or found nothing newer.
     */
    public static final Duration TOP_UP_RETRY = Duration.ofHours(1);

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
     * How many branches whose checks are pending have their checks read with their timing at once.
     */
    public static final int PENDING_BRANCHES = 10;

    private CheckTimings() {}
}
