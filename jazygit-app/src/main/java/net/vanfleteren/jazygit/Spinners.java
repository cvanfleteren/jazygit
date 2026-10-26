package net.vanfleteren.jazygit;

import java.time.Instant;
import net.vanfleteren.jazygit.feature.github.checks.ChecksDialog;
import net.vanfleteren.jazygit.feature.github.comments.CommentsDialog;
import net.vanfleteren.jazygit.feature.github.show.PullRequestDialog;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.FetchLine;

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
                || CommentsDialog.showsSpinner(model)
                || PullRequestDialog.showsSpinner(model)
                || ChecksDialog.showsSpinner(model)
                || FetchLine.showsSpinner(model, now);
    }
}
