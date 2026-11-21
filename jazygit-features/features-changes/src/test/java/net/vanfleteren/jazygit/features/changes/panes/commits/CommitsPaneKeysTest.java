package net.vanfleteren.jazygit.features.changes.panes.commits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneLine;
import org.junit.jupiter.api.Test;

/**
 * The commits pane shows what any feature gives for its keys (ADR 172), knowing no feature: a line above the list, and
 * a listing in place of the log.
 */
class CommitsPaneKeysTest {

    private static final Model LOG = TestModels.loaded(SampleData.git())
            .mapRepo(repo -> repo.withCommits(
                    new Loadable.Loaded<>(List.of(commit("mmmmmmm", "bbbbbbb", "aaaaaaa"), commit("aaaaaaa")))));

    private static final PaneLine LINE =
            new PaneLine("ab", text -> new Probe("edited " + text), new Probe("submitted"), new Probe("cancelled"));

    @Test
    void anOpenLineTakesTheKeysThatEditItEnterAndEscape() throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(sent::add, Fills.NONE.with(CommitsPane.LINE, model -> Option.of(LINE)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(LOG, CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            assertTrue(RenderedText.of(testRunner, () -> panel.render(LOG, CommitsPanel.PANE))
                    .contains("/ ab"));
            SettledPilot.of(testRunner).press('c');
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(List.of(new Probe("edited abc"), new Probe("submitted"), new Probe("cancelled")), shown(sent));
    }

    @Test
    void aListingIsListedInPlaceOfTheLogWithoutRailsUnderItsTitle() throws Exception {
        CommitsListing listing = new CommitsListing(
                "Found",
                new Loadable.Loaded<>(List.of(commit("mmmmmmm", "bbbbbbb", "fffff02"))),
                "None",
                new Probe("cleared"));
        CommitsPanel panel =
                new CommitsPanel(msg -> {}, Fills.NONE.with(CommitsPane.LISTING, model -> Option.of(listing)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(LOG, CommitsPanel.PANE))) {
            assertTrue(RenderedText.of(testRunner, () -> panel.render(LOG, CommitsPanel.PANE))
                    .contains("Found"));
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                List.of("mmmmmmm Ad ◎ msg mmmmmmm"),
                panel.shownRows().map(row -> row.text()).toList());
    }

    @Test
    void anEmptyListingSaysWhatItGives() throws Exception {
        CommitsListing listing =
                new CommitsListing("Found", new Loadable.Loaded<>(List.empty()), "Nothing here", new Probe("cleared"));
        CommitsPanel panel =
                new CommitsPanel(msg -> {}, Fills.NONE.with(CommitsPane.LISTING, model -> Option.of(listing)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(LOG, CommitsPanel.PANE))) {
            assertTrue(RenderedText.of(testRunner, () -> panel.render(LOG, CommitsPanel.PANE))
                    .contains("Nothing here"));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void escapeClearsAListingBeforeItLetsGoOfTheFilesLockedToTheCommits() throws Exception {
        CommitsListing listing = new CommitsListing(
                "Found", new Loadable.Loaded<>(List.of(commit("aaaaaaa"))), "None", new Probe("cleared"));
        Model locked = LOG.mapRepoView(
                repoView -> repoView.mapFiles(files -> files.lockedTo(CommitsPanel.PANE, FilesLock.In.LIST)));

        assertEquals(
                List.of(new Probe("cleared")),
                escaped(locked, Fills.NONE.with(CommitsPane.LISTING, model -> Option.of(listing))));
        assertEquals(List.of(new SelectionMsg.FilesUnlocked()), escaped(locked, Fills.NONE));
    }

    /**
     * What Escape in the commits of {@code model}, with the focus, sends with {@code fills}.
     */
    private static List<Msg> escaped(Model model, Fills fills) throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        Consumer<Msg> dispatch = sent::add;
        CommitsPanel panel = new CommitsPanel(dispatch, fills);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }
        return shown(sent);
    }

    private static List<Msg> shown(java.util.List<Msg> sent) {
        return List.ofAll(sent).filter(msg -> !(msg instanceof SelectionMsg.CommitSelected));
    }

    private static Commit commit(String sha, String... parents) {
        return Fixtures.commit(
                new Sha(sha + "0000"),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                "msg " + sha,
                "",
                List.of(parents).map(parent -> new Sha(parent + "0000")));
    }

    private record Probe(String name) implements Msg {

        @Override
        public Next apply(Model model) {
            return Next.of(model);
        }
    }
}
