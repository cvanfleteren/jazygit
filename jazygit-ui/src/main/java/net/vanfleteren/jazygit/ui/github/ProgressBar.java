package net.vanfleteren.jazygit.ui.github;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * A bar of how far something is, on one line (ADR 89): a thick line for the part done, in a colour of its own, and a
 * thin dimmed one for the rest, {@code ━━━━━━────}. It is full only once it is done, not once the part done rounds up
 * to the whole width.
 */
public final class ProgressBar {

    private static final String DONE = "━";
    private static final String LEFT = "─";
    private static final String UNKNOWN = "╌";

    private ProgressBar() {}

    /**
     * A bar {@code width} columns wide, {@code progress} of it done, from 0 to 1, in {@code cssClass}.
     */
    public static List<StyledElement<?>> of(double progress, int width, String cssClass) {
        int done = progress >= 1 ? width : Math.clamp((long) Math.floor(width * progress), 0, width - 1);
        // Without an empty piece on either side.
        return List.<StyledElement<?>>empty()
                .appendAll(Option.when(done > 0, () -> text(DONE.repeat(done)).addClass(cssClass)))
                .appendAll(Option.when(
                        done < width, () -> text(LEFT.repeat(width - done)).addClass(Theme.Classes.DIM)));
    }

    /**
     * A bar {@code width} columns wide of something of which it is not known how far it is: dashed, dimmed.
     */
    public static StyledElement<?> unknown(int width) {
        return text(UNKNOWN.repeat(width)).addClass(Theme.Classes.DIM);
    }

    /**
     * How far {@code progress} is, from 0 to 1, in whole percents, rounded down so it says 100% only once it is
     * full: {@code 62%}.
     */
    public static int percent(double progress) {
        return progress >= 1 ? 100 : Math.min(99, (int) Math.floor(progress * 100));
    }
}
