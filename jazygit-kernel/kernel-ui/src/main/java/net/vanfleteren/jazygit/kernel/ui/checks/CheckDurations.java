package net.vanfleteren.jazygit.kernel.ui.checks;

import java.time.Duration;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * How long checks took or run, written the same by every forge (ADR 32): to the second, from an hour on to the minute.
 */
public final class CheckDurations {

    private static final long HOUR = 3600;

    private CheckDurations() {}

    /**
     * How long it is since {@code since}, rounded down as a clock that counts up, as it is exact: to the second,
     * {@code 0s}, {@code 47s}, {@code 2m}, {@code 7m 41s}; from an hour on to the minute, {@code 1h 5m}.
     */
    public static String exact(Duration since) {
        long seconds = Math.max(0, since.toSeconds());
        return seconds >= HOUR ? minutes(seconds / 60) : minutesAndSeconds(seconds);
    }

    /**
     * {@code seconds}, under an hour: {@code 45s}, {@code 2m}, {@code 2m 5s}.
     */
    public static String minutesAndSeconds(long seconds) {
        return seconds < 60
                ? Messages.get("checks.seconds", String.valueOf(seconds))
                : seconds % 60 == 0
                        ? minutes(seconds / 60)
                        : Messages.get(
                                "checks.minutesSeconds", String.valueOf(seconds / 60), String.valueOf(seconds % 60));
    }

    /**
     * {@code minutes}, one or more: {@code 2m}, {@code 1h}, {@code 1h 5m}; a whole hour without its {@code 0m}, as a
     * whole minute is without its {@code 0s}.
     */
    public static String minutes(long minutes) {
        return minutes < 60
                ? Messages.get("checks.minutes", String.valueOf(minutes))
                : minutes % 60 == 0
                        ? Messages.get("checks.wholeHours", String.valueOf(minutes / 60))
                        : Messages.get("checks.hours", String.valueOf(minutes / 60), String.valueOf(minutes % 60));
    }
}
