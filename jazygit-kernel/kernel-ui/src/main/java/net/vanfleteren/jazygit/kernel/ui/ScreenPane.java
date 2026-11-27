package net.vanfleteren.jazygit.kernel.ui;

import java.time.Clock;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * A pane a feature brings to a screen (ADR 211), with {@link Feature#pane}: the {@link LeftColumn} makes it once, and
 * shows it while the screen is, where {@link Screen#panes()} lists its id. The panes of the repository screen are
 * brought so, by the groups of features that own them, as those of a feature's own screen are.
 *
 * @param screen  the screen it is shown on
 * @param factory what makes it, once the column knows what a pane is made with
 */
public record ScreenPane(Screen screen, Factory factory) {

    /**
     * What makes a pane, once the column knows what it is made with.
     */
    @FunctionalInterface
    public interface Factory {

        LeftPane of(Context context);
    }

    /**
     * What a pane is made with.
     *
     * @param dispatch    receives the messages for the user's actions
     * @param openContent moves the focus to the content shown for the pane given
     * @param highlights  how the highlighted row is shown, also when the pane is not in use
     * @param fills       what the features give for the slots, which a pane asks for its own (ADR 172)
     * @param clock       turns the spinners a pane shows; the time a pane says is the model's
     */
    public record Context(
            Consumer<Msg> dispatch,
            Consumer<PaneId> openContent,
            Theme.Highlights highlights,
            Fills fills,
            Clock clock) {}
}
