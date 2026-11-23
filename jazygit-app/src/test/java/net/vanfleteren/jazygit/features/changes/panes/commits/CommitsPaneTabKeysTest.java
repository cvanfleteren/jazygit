package net.vanfleteren.jazygit.features.changes.panes.commits;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.view.TabId;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.commits.CommitRows;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * {@code [} and {@code ]} go round the tabs of the commits pane (ADR 185), from the log and from a tab whose scope hosts
 * those keys, with the app's keymap.
 */
class CommitsPaneTabKeysTest {

    private static final Model LOG = TestModels.loaded(SampleData.git())
            .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(commit("aaaaaaa")))));

    private static final TabId OTHER = new TabId("test.other");

    // A tab listing commits under the keys of the commits, which host those of the tabs.
    private static final PaneTab<Commit> TAB = new PaneTab<>(
            OTHER,
            "Other",
            Scopes.COMMITS,
            new Loadable.Loaded<>(List.of(commit("bbbbbbb"))),
            commit -> LoadableList.Item.of(CommitRows.row(commit, "○ ", false)),
            Option.none(),
            commit -> Option.none());

    private static final Fills WITH_TAB = Fills.NONE.with(CommitsPane.TABS, model -> Option.of(TAB));

    @Test
    void fromTheLogTheBracketsGoToTheTabAfterOrBeforeItGoingRound() throws Exception {
        assertEquals(List.of(new ViewMsg.TabChosen(CommitsPanel.PANE, OTHER)), pressed(LOG, WITH_TAB, ']'));
        assertEquals(List.of(new ViewMsg.TabChosen(CommitsPanel.PANE, OTHER)), pressed(LOG, WITH_TAB, '['));
    }

    @Test
    void fromATabOfAFeatureTheBracketsGoBackToTheLog() throws Exception {
        Model onOther = LOG.mapRepoView(view -> view.withTab(CommitsPanel.PANE, OTHER));

        assertEquals(
                List.of(new ViewMsg.TabChosen(CommitsPanel.PANE, CommitsPane.LOG)), pressed(onOther, WITH_TAB, ']'));
    }

    @Test
    void withOnlyTheLogTheBracketsDoNothing() throws Exception {
        assertEquals(List.empty(), pressed(LOG, Fills.NONE, ']'));
    }

    /**
     * What pressing {@code key} in the commits pane of {@code model}, with the focus, sends of the tabs.
     */
    private static List<Msg> pressed(Model model, Fills fills, char key) throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(sent::add, fills);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            SettledPilot.of(testRunner).press(key);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }
        return List.ofAll(sent).filter(ViewMsg.TabChosen.class::isInstance);
    }

    private static Commit commit(String sha) {
        return Fixtures.commit(
                new Sha(sha + "0000"), "Ada", "ada@example.com", Instant.EPOCH, "msg " + sha, "", List.empty());
    }
}
