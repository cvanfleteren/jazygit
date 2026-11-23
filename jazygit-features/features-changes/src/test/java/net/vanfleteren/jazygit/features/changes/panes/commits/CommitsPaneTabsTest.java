package net.vanfleteren.jazygit.features.changes.panes.commits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.view.TabId;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneLine;
import org.junit.jupiter.api.Test;

/**
 * The log is the first tab of the commits pane, and the features add others (ADR 185), which the pane shows knowing
 * nothing of what they list.
 */
class CommitsPaneTabsTest {

    private static final Model LOG = TestModels.loaded(SampleData.git())
            .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(commit("aaaaaaa")))));

    private static final TabId NAMES = new TabId("test.names");

    private static final Keymap.Scope<String> NAMES_SCOPE = new Keymap.Scope<>("testNames", "panel.commits.title");

    private static final PaneTab<String> TAB = new PaneTab<>(
            NAMES,
            "Names",
            NAMES_SCOPE,
            new Loadable.Loaded<>(List.of("ada", "grace")),
            name -> LoadableList.Item.of(LoadableList.Row.of("name " + name)),
            Option.of("No names"),
            name -> name.map(Probe::new));

    private static final Fills WITH_TAB = Fills.NONE.with(CommitsPane.TABS, model -> Option.of(TAB));

    @Test
    void withoutATabOfAFeatureTheTitleNamesOnlyTheLog() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {});

        String title = rendered(panel, LOG).lines().findFirst().orElseThrow();

        assertTrue(title.contains("Commits of main"), title);
        assertFalse(title.contains(" │ "), title);
    }

    @Test
    void withTabsTheLogIsNamedShortAndALineAtItsBottomSaysWhatItIsTheLogOf() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {}, WITH_TAB);

        java.util.List<String> lines = rendered(panel, LOG).lines().toList();

        assertTrue(lines.getFirst().contains("Commits │ Names"), lines.getFirst());
        assertFalse(lines.getFirst().contains("main"), lines.getFirst());
        assertEquals(List.of("aaaaaaa"), panel.shownRows().map(row -> row.text().substring(0, 7)));
        // The line just above the bottom border.
        assertTrue(lines.get(lines.size() - 2).contains("main"), String.join("\n", lines));
    }

    @Test
    void theLineAtTheBottomBelongsToTheLog() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {}, WITH_TAB);

        java.util.List<String> lines =
                rendered(panel, shownOn(LOG, NAMES)).lines().toList();

        assertFalse(lines.get(lines.size() - 2).contains("main"), String.join("\n", lines));
    }

    @Test
    void aDetachedHeadIsNamedByItsCommit() {
        Model detached = LOG.mapRepo(repo -> repo.withStatus(
                repo.status().map(status -> status.withHead(new Head.Detached(new Sha("1234567890abcdef"))))));

        assertEquals("detached at 1234567", CommitsPanel.headName(detached));
        assertEquals("main", CommitsPanel.headName(LOG));
    }

    @Test
    void theTabTheModelShowsIsListedAndItsHighlightedRowSaysWhatToShow() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {}, WITH_TAB);
        Model onNames = shownOn(LOG, NAMES);

        rendered(panel, onNames);

        assertEquals(List.of("name ada", "name grace"), panel.shownRows().map(LoadableList.Row::text));
        assertEquals(Option.none(), panel.selectedCommit());
        assertEquals(Option.of(new Probe("ada")), panel.selectionChange(onNames));
    }

    @Test
    void aTabNoFeatureGivesAnyMoreFallsBackToTheLog() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {});
        Model onNames = shownOn(LOG, NAMES);

        rendered(panel, onNames);

        assertEquals(1, panel.shownRows().size());
        assertTrue(panel.selectedCommit().isDefined());
    }

    @Test
    void theLineAndTheListingBelongToTheLog() throws Exception {
        PaneLine line = new PaneLine("ab", text -> new Probe(text), new Probe("submitted"), new Probe("cancelled"));
        CommitsListing listing =
                new CommitsListing("Found", new Loadable.Loaded<>(List.empty()), "Nothing found", new Probe("cleared"));
        CommitsPanel panel = new CommitsPanel(
                msg -> {},
                WITH_TAB.with(CommitsPane.LINE, model -> Option.of(line))
                        .with(CommitsPane.LISTING, model -> Option.of(listing)));

        String onLog = rendered(panel, LOG);
        String onNames = rendered(panel, shownOn(LOG, NAMES));

        // What is listed in place of the log names itself in the line at the bottom, as the log does.
        assertTrue(onLog.contains("Commits │ Names") && onLog.contains("/ ab"), onLog);
        java.util.List<String> logLines = onLog.lines().toList();
        assertTrue(logLines.get(logLines.size() - 2).contains("Found"), onLog);
        assertFalse(onNames.contains("/ ab"), onNames);
        assertTrue(onNames.contains("name ada"), onNames);
    }

    @Test
    void anEmptyTabSaysWhatItsFeatureGives() throws Exception {
        PaneTab<String> empty = new PaneTab<>(
                NAMES,
                "Names",
                NAMES_SCOPE,
                new Loadable.Loaded<>(List.empty()),
                name -> LoadableList.Item.of(LoadableList.Row.of(name)),
                Option.of("No names"),
                name -> Option.none());
        CommitsPanel panel = new CommitsPanel(msg -> {}, Fills.NONE.with(CommitsPane.TABS, model -> Option.of(empty)));

        assertTrue(rendered(panel, shownOn(LOG, NAMES)).contains("No names"));
    }

    @Test
    void clickingTheNameOfATabShowsIt() throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(sent::add, WITH_TAB);
        int log = "Commits".length();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(LOG, CommitsPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(LOG, CommitsPanel.PANE));
            // The title starts after the corner of the border.
            SettledPilot.of(testRunner).click(log + 4, 0);
            SettledPilot.of(testRunner).click(1, 0);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                List.of(
                        new ViewMsg.TabChosen(CommitsPanel.PANE, NAMES),
                        new ViewMsg.TabChosen(CommitsPanel.PANE, CommitsPane.LOG)),
                List.ofAll(sent).filter(ViewMsg.TabChosen.class::isInstance));
    }

    private static Model shownOn(Model model, TabId tab) {
        return model.mapRepoView(view -> view.withTab(CommitsPanel.PANE, tab));
    }

    private static String rendered(CommitsPanel panel, Model model) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            String shown = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            SettledPilot.of(testRunner).quit();
            return shown;
        }
    }

    private static Commit commit(String sha) {
        return Fixtures.commit(
                new Sha(sha + "0000"), "Ada", "ada@example.com", Instant.EPOCH, "msg " + sha, "", List.empty());
    }

    private record Probe(String name) implements Msg {

        @Override
        public Next apply(Model model) {
            return Next.of(model);
        }
    }
}
