package net.vanfleteren.jazygit.ui;

import dev.tamboui.widgets.spinner.SpinnerStyle;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * Text that depends on the time: how long ago something happened, and the spinner of something that runs.
 */
public final class TimeText {

    /**
     * How long a spinner shows each of its frames.
     */
    public static final Duration SPINNER_FRAME = Duration.ofMillis(120);

    /**
     * How often the model is woken up when nothing else happens ({@code Msg.Clock}), so that an age shows at most
     * this late.
     */
    public static final Duration CLOCK_INTERVAL = Duration.ofSeconds(1);

    // The steps in which what is read every minute counts its age under a minute.
    private static final int READ_STEP_SECONDS = 10;

    private static final SpinnerStyle SPINNER = SpinnerStyle.BOUNCING_BAR;

    private TimeText() {}

    /**
     * The time {@code since} as a number and one unit: m, h or d; or "now" under a minute, so that what shows it changes
     * once a minute at most.
     */
    public static String age(Duration since) {
        long seconds = Math.max(0, since.toSeconds());
        return switch (Long.valueOf(seconds)) {
            case Long s when s < 60 -> Messages.get("branches.age.now");
            case Long s when s < 3600 -> Messages.get("branches.age.minutes", String.valueOf(s / 60));
            case Long s when s < 86400 -> Messages.get("branches.age.hours", String.valueOf(s / 3600));
            case Long s -> Messages.get("branches.age.days", String.valueOf(s / 86400));
        };
    }

    /**
     * How long ago something happened {@code since}, to show in a sentence: "2m ago", or "just now" under a minute.
     */
    public static String ago(Duration since) {
        return since.toSeconds() < 60
                ? Messages.get("branches.age.justNow")
                : Messages.get("branches.age.ago", age(since));
    }

    /**
     * How long ago something that is read every minute, like the remotes, was read {@code since}, to show in a
     * sentence: by 10s under a minute ("20s ago", or "just now" under 10s), and as {@link #ago(Duration)} from a
     * minute on. Both when it was read and the time it is now are exact (ADR 33), so each step shows from the moment
     * it is reached.
     */
    public static String readAgo(Duration since) {
        long seconds = Math.max(0, since.toSeconds()) / READ_STEP_SECONDS * READ_STEP_SECONDS;
        return seconds < READ_STEP_SECONDS
                ? Messages.get("branches.age.justNow")
                : seconds < 60
                        ? Messages.get(
                                "branches.age.ago", Messages.get("branches.age.seconds", String.valueOf(seconds)))
                        : ago(since);
    }

    /**
     * The frame of the spinner of something that runs to show at {@code now}.
     */
    public static String spinnerFrame(Instant now) {
        return SPINNER.frame((int) (now.toEpochMilli() / SPINNER_FRAME.toMillis() % SPINNER.frameCount()));
    }
}
