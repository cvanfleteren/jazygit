package net.vanfleteren.jazygit.app;

import java.time.Instant;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.FetchLine;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;

/**
 * The spinners on the screen, which only turn while frames are drawn: their frame is told by the clock.
 */
final class Spinners {

    private Spinners() {}

    /**
     * Whether a view shows a spinner in {@code model} at {@code now}, so frames must be drawn for it to turn.
     */
    static boolean shown(Model model, Instant now) {
        return BranchesPanel.showsSpinner(model)
                || Features.ALL.exists(feature -> feature.spinning().test(model))
                || FetchLine.showsSpinner(model, now);
    }
}
