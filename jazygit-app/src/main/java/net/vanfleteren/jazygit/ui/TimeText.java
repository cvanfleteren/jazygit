package net.vanfleteren.jazygit.ui;

import dev.tamboui.widgets.spinner.SpinnerStyle;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * Text that depends on the time: how long ago something happened, and the spinner of something that runs.
 */
public final class TimeText {

    private static final SpinnerStyle SPINNER = SpinnerStyle.BOUNCING_BAR;
    private static final long SPINNER_FRAME_MILLIS = 120;

    private TimeText() {}

    /**
     * The time {@code since} as a number and one unit: s, m, h or d.
     */
    public static String age(Duration since) {
        long seconds = Math.max(0, since.toSeconds());
        return seconds < 60
                ? Messages.get("branches.age.seconds", String.valueOf(seconds))
                : seconds < 3600
                        ? Messages.get("branches.age.minutes", String.valueOf(seconds / 60))
                        : seconds < 86400
                                ? Messages.get("branches.age.hours", String.valueOf(seconds / 3600))
                                : Messages.get("branches.age.days", String.valueOf(seconds / 86400));
    }

    /**
     * The frame of the spinner of something that runs to show at {@code now}.
     */
    public static String spinnerFrame(Instant now) {
        return SPINNER.frame((int) (now.toEpochMilli() / SPINNER_FRAME_MILLIS % SPINNER.frameCount()));
    }
}
