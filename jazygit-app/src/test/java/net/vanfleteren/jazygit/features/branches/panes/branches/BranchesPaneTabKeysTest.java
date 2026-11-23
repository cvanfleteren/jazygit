package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.state.view.TabId;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.commits.CommitRows;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * {@code [} and {@code ]} go round the tabs of the branches pane (ADR 185), its own and those the features add, from
 * each, with the app's keymap.
 */
class BranchesPaneTabKeysTest {

    private static final Model LOADED = TestModels.loaded(SampleData.git());

    private static final TabId OTHER = new TabId("test.other");

    // A tab listing commits under the keys of the commits, which host those of the tabs.
    private static final PaneTab<Commit> TAB = new PaneTab<>(
            OTHER,
            "Other",
            Scopes.COMMITS,
            new Loadable.Loaded<>(List.of(Fixtures.commit(
                    new Sha("bbbbbbb0000"), "Ada", "ada@example.com", Instant.EPOCH, "msg", "", List.empty()))),
            commit -> LoadableList.Item.of(CommitRows.row(commit, "○ ", false)),
            Option.none(),
            commit -> Option.none());

    private static final Fills WITH_TAB = Fills.NONE.with(BranchesPane.TABS, model -> Option.of(TAB));

    @Test
    void fromTheLocalBranchesTheBracketsGoToTheTabAfterOrBeforeItGoingRound() throws Exception {
        assertEquals(List.of(chosen(BranchesTab.REMOTE.id())), pressed(LOADED, Fills.NONE, ']'));
        assertEquals(List.of(chosen(BranchesTab.WORKTREES.id())), pressed(LOADED, Fills.NONE, '['));
        assertEquals(List.of(chosen(OTHER)), pressed(LOADED, WITH_TAB, '['));
    }

    @Test
    void fromTheWorktreesTheNextTabIsThatOfAFeature() throws Exception {
        Model onWorktrees = LOADED.mapRepoView(view -> view.withBranchesTab(BranchesTab.WORKTREES));

        assertEquals(List.of(chosen(OTHER)), pressed(onWorktrees, WITH_TAB, ']'));
    }

    @Test
    void fromATabOfAFeatureTheBracketsGoRoundToTheLocalBranches() throws Exception {
        Model onOther = LOADED.mapRepoView(view -> view.withTab(BranchesPanel.PANE, OTHER));

        assertEquals(List.of(chosen(BranchesTab.LOCAL.id())), pressed(onOther, WITH_TAB, ']'));
        assertEquals(List.of(chosen(BranchesTab.WORKTREES.id())), pressed(onOther, WITH_TAB, '['));
    }

    private static ViewMsg.TabChosen chosen(TabId tab) {
        return new ViewMsg.TabChosen(BranchesPanel.PANE, tab);
    }

    /**
     * What pressing {@code key} in the branches pane of {@code model}, with the focus, sends of the tabs.
     */
    private static List<Msg> pressed(Model model, Fills fills, char key) throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(sent::add, Clock.systemUTC(), Theme.Highlights.DEFAULT, fills);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(model, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(key);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }
        return List.ofAll(sent).filter(ViewMsg.TabChosen.class::isInstance);
    }
}
