package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.state.view.TabId;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * The local branches, the remote ones and the worktrees are the branches pane's own tabs, and the features add others
 * after them (ADR 185), which the pane shows knowing nothing of what they list.
 */
class BranchesPaneTabsTest {

    private static final Model LOADED = TestModels.loaded(SampleData.git());

    private static final TabId NAMES = new TabId("test.names");

    private static final Keymap.Scope<String> NAMES_SCOPE = new Keymap.Scope<>("testNames", "panel.branches.title");

    private static final PaneTab<String> TAB = new PaneTab<>(
            NAMES,
            "Names",
            NAMES_SCOPE,
            new Loadable.Loaded<>(List.of("ada", "grace")),
            name -> LoadableList.Item.of(LoadableList.Row.of("name " + name)),
            Option.of("No names"),
            name -> name.map(Probe::new));

    private static final Fills WITH_TAB = Fills.NONE.with(BranchesPane.TABS, model -> Option.of(TAB));

    @Test
    void withoutATabOfAFeatureTheTitleNamesTheThreeListsOfThePane() throws Exception {
        String title = rendered(new BranchesPanel(msg -> {}), LOADED)
                .lines()
                .findFirst()
                .orElseThrow();

        assertTrue(title.contains("Branches │ Remotes │ Worktrees"), title);
        assertFalse(title.contains("Names"), title);
    }

    @Test
    void theTabOfAFeatureIsNamedAfterThoseOfThePane() throws Exception {
        String title = rendered(panel(msg -> {}), LOADED).lines().findFirst().orElseThrow();

        assertTrue(title.contains("Branches │ Remotes │ Worktrees │ Names"), title);
    }

    @Test
    void theTabTheModelShowsIsListedAndItsHighlightedRowSaysWhatToShow() throws Exception {
        BranchesPanel panel = panel(msg -> {});
        Model onNames = shownOn(LOADED, NAMES);

        String shown = rendered(panel, onNames);

        assertTrue(shown.contains("name ada") && shown.contains("name grace"), shown);
        assertEquals(
                Option.of(List.of("name ada", "name grace")),
                panel.addedRows().map(rows -> rows.map(LoadableList.Row::text)));
        assertEquals(Option.none(), onNames.repoView().branchesTab());
        assertEquals(Option.of(new Probe("ada")), panel.selectionChange(onNames));
    }

    @Test
    void aTabNoFeatureGivesAnyMoreFallsBackToTheLocalBranches() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> {});

        String shown = rendered(panel, shownOn(LOADED, NAMES));

        assertFalse(shown.contains("name ada"), shown);
        assertEquals(Option.none(), panel.addedRows());
    }

    @Test
    void clickingTheNameOfATabShowsIt() throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        BranchesPanel panel = panel(sent::add);
        // The names of the pane's own tabs and their separators come before that of the feature's.
        int before = List.of(BranchesTab.values())
                .map(tab -> BranchesPanel.tabName(tab).length() + " │ ".length())
                .sum()
                .intValue();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(LOADED, BranchesPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(LOADED, BranchesPanel.PANE));
            // The title starts after the corner of the border.
            SettledPilot.of(testRunner).click(before + 1, 0);
            SettledPilot.of(testRunner).click(1, 0);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                List.of(
                        new ViewMsg.TabChosen(BranchesPanel.PANE, NAMES),
                        new ViewMsg.TabChosen(BranchesPanel.PANE, BranchesTab.LOCAL.id())),
                List.ofAll(sent).filter(ViewMsg.TabChosen.class::isInstance));
    }

    private static BranchesPanel panel(java.util.function.Consumer<Msg> dispatch) {
        return new BranchesPanel(dispatch, java.time.Clock.systemUTC(), Theme.Highlights.DEFAULT, WITH_TAB);
    }

    private static Model shownOn(Model model, TabId tab) {
        return model.mapRepoView(view -> view.withTab(BranchesPanel.PANE, tab));
    }

    private static String rendered(BranchesPanel panel, Model model) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, BranchesPanel.PANE))) {
            String shown = RenderedText.of(testRunner, () -> panel.render(model, BranchesPanel.PANE));
            SettledPilot.of(testRunner).quit();
            return shown;
        }
    }

    private record Probe(String name) implements Msg {

        @Override
        public Next apply(Model model) {
            return Next.of(model);
        }
    }
}
