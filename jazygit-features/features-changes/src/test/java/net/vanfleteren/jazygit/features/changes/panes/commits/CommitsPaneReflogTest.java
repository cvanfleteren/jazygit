package net.vanfleteren.jazygit.features.changes.panes.commits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.CommitKind;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogView;
import net.vanfleteren.jazygit.features.changes.reflog.content.ReflogShown;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * The reflog of HEAD, a tab of the commits pane after the log (ADR 186), as the pane draws it.
 */
class CommitsPaneReflogTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_000_000);
    private static final Model LOADED = TestModels.loaded(SampleData.git()).withNow(NOW);
    private static final Sha HEAD =
            LOADED.repo().status().toOption().get().head().commit().get();
    private static final Sha OTHER = new Sha("b".repeat(40));
    private static final Fills REFLOG = Fills.NONE.with(CommitsPane.TABS, ReflogShown::tab);

    @Test
    void theReflogIsATabAfterTheLogListingAnEntryARow() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {}, REFLOG);
        Model model = shown(List.of(
                entry(0, 120, "checkout: moving from main to x", new Action.Checkout("main", "x"), HEAD),
                entry(1, 7_200, "commit: Add a", new Action.Committed(CommitKind.PLAIN, "Add a"), OTHER)));

        String shown = rendered(panel, model);

        assertTrue(shown.contains("│ Reflog"), shown);
        assertEquals(
                List.of(
                        "◆ 2m   checkout      main → x " + HEAD.shortSha(),
                        "  2h   commit        Add a " + OTHER.shortSha()),
                panel.shownRows().map(LoadableList.Row::text));
        assertEquals(Option.none(), panel.selectedCommit());
        assertEquals(Option.of(new SelectionMsg.CommitSelected(HEAD)), panel.selectionChange(model));
    }

    @Test
    void theHighlightStaysOnItsEntryAsNewOnesComeOnTop() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {}, REFLOG);
        ReflogEntry older = entry(0, 7_200, "commit: Add a", new Action.Committed(CommitKind.PLAIN, "Add a"), OTHER);
        ReflogEntry newer = entry(0, 60, "reset: moving to HEAD~1", new Action.Reset("HEAD~1"), HEAD);
        Model before = shown(List.of(older));
        Model after = shown(
                List.of(newer, new ReflogEntry(1, older.time(), older.subject(), older.action(), older.commit())));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, CommitsPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(before, CommitsPanel.PANE));
            RenderedText.of(testRunner, () -> panel.render(after, CommitsPanel.PANE));
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(Option.of(new SelectionMsg.CommitSelected(OTHER)), panel.selectionChange(after));
    }

    @Test
    void anEmptyReflogSaysWhy() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {}, REFLOG);
        Model model = LOADED.withPart(
                        ReflogView.PART,
                        ReflogView.NONE
                                .withEntries(new Loadable.Loaded<>(List.empty()))
                                .withEmpty(Option.of(net.vanfleteren.jazygit.core.repo.Reflog.Empty.OFF)))
                .mapRepoView(view -> view.withTab(CommitsPanel.PANE, ReflogView.TAB));

        String shown = rendered(panel, model);

        assertTrue(shown.contains("git keeps no reflogs here"), shown);
    }

    private static Model shown(List<ReflogEntry> entries) {
        return LOADED.withPart(ReflogView.PART, ReflogView.NONE.withEntries(new Loadable.Loaded<>(entries)))
                .mapRepoView(view -> view.withTab(CommitsPanel.PANE, ReflogView.TAB));
    }

    private static String rendered(CommitsPanel panel, Model model) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            String shown = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            SettledPilot.of(testRunner).quit();
            return shown;
        }
    }

    private static ReflogEntry entry(int place, long secondsAgo, String subject, Action action, Sha sha) {
        return new ReflogEntry(
                place,
                NOW.minusSeconds(secondsAgo),
                subject,
                action,
                Fixtures.commit(sha, "Ada", "ada@example.com", Instant.EPOCH, "c", "", List.empty()));
    }
}
