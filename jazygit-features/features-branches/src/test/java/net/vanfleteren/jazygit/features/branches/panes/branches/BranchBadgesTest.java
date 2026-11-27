package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * The branches pane draws what the features show of a branch, which it is given and knows nothing of (ADR 167); how
 * the badges of the features are joined is tested in {@code kernel-ui}.
 */
class BranchBadgesTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    /**
     * A feature that shows {@code mark} and the name of each local branch, and nothing of a remote one.
     */
    private static BranchBadges.Badge marking(String mark) {
        return (model, ref) -> ref instanceof BranchRef.Local(BranchName name)
                ? BranchBadges.Shown.of(List.of(LoadableList.Seg.of(" " + mark + name.value())))
                : BranchBadges.Shown.NONE;
    }

    @Test
    void thePaneDrawsTheBadgesItIsGivenBelowEachBranch() throws Exception {
        BranchesPanel panel = new BranchesPanel(
                msg -> {}, Clock.systemUTC(), Theme.Highlights.DEFAULT, new BranchBadges(List.of(marking("★"))));
        Model model = TestModels.loaded();

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> panel.render(model, BranchesPanel.PANE))) {
            String screen = RenderedText.of(runner, () -> panel.render(model, BranchesPanel.PANE));

            // On line 2 of the branch, after its age.
            assertTrue(screen.contains("now  ★main"), screen);
            SettledPilot.of(runner).quit();
        }
    }

    @Test
    void aRowIsDrawnAgainWhenTheTextOfItsBadgesThatChangesWithTheTimeChanges() {
        Instant done = NOW.plusSeconds(90);
        BranchBadges badges = new BranchBadges(List.of(
                (model, ref) -> new BranchBadges.Shown(List.empty(), now -> now.isBefore(done) ? "1m" : "done")));

        // The branches of the sample model are decades old, so their age does not change before it.
        assertEquals(
                Option.of(done),
                ShownTime.next(BranchesPanel.shownTimes(TestModels.loaded().withNow(NOW), badges), NOW));
    }
}
