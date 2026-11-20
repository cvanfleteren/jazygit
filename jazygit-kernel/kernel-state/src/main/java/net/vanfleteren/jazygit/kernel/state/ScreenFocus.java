package net.vanfleteren.jazygit.kernel.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;

/**
 * Where the focus was on a screen a feature brings, which it gets back when it is shown again (ADR 168). The
 * repository screen keeps its own in what it shows ({@code RepoView}).
 *
 * @param last the pane, or the area of the content panel, that had the focus last; none before one had it
 * @param pane the pane of the screen that had the focus last, whose highlighted item the content panel shows; none
 *             before one had it
 */
public record ScreenFocus(Option<Focus.OnScreen> last, Option<PaneId> pane) {

    /**
     * Where the focus is on a screen not shown yet: nowhere.
     */
    public static final ScreenFocus NONE = new ScreenFocus(Option.none(), Option.none());

    /**
     * This, with {@code focus} on {@code screen} now: the pane too, when it is one of the panes of the screen.
     */
    ScreenFocus on(Focus.OnScreen focus, Screen screen) {
        return new ScreenFocus(
                Option.of(focus),
                focus instanceof PaneId shown && screen.panes().contains(shown) ? Option.of(shown) : pane);
    }
}
