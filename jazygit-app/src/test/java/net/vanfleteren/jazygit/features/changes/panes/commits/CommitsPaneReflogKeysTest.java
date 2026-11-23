package net.vanfleteren.jazygit.features.changes.panes.commits;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.copy.CopyMsg;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogView;
import net.vanfleteren.jazygit.features.changes.reflog.content.ReflogShown;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The keys of the reflog's tab of the commits pane, with the app's keymap (ADR 186): Enter opens the files of the
 * entry's commit, {@code y} copies its sha, as it does on the log, and the brackets go back to the log.
 */
class CommitsPaneReflogKeysTest {

    private static final Sha SHA = new Sha("c".repeat(40));
    private static final ReflogEntry ENTRY = new ReflogEntry(
            0,
            Instant.EPOCH,
            "reset: moving to HEAD~1",
            new Action.Reset("HEAD~1"),
            Fixtures.commit(SHA, "Ada", "ada@example.com", Instant.EPOCH, "c", "", List.empty()));
    private static final Model LOG = TestModels.loaded(SampleData.git());
    private static final Model REFLOG = LOG.withPart(
                    ReflogView.PART, ReflogView.NONE.withEntries(new Loadable.Loaded<>(List.of(ENTRY))))
            .mapRepoView(view -> view.withTab(CommitsPanel.PANE, ReflogView.TAB));
    private static final Fills FILLS = Fills.NONE.with(CommitsPane.TABS, ReflogShown::tab);

    @Test
    void enterOpensTheFilesOfTheCommitOfTheEntry() throws Exception {
        assertEquals(
                List.of(new CommitFilesMsg.Opened(new Changes.OfCommit(SHA), CommitsPanel.PANE)),
                pressed(REFLOG, KeyCode.ENTER, CommitFilesMsg.Opened.class));
    }

    @Test
    void yCopiesTheShaOfAnEntryAndOfACommitOfTheLog() throws Exception {
        Sha logged = LOG.repo().commits().toOption().get().head().sha();

        assertEquals(List.of(new CopyMsg.ShaAsked(SHA)), pressed(REFLOG, 'y', CopyMsg.ShaAsked.class));
        assertEquals(List.of(new CopyMsg.ShaAsked(logged)), pressed(LOG, 'y', CopyMsg.ShaAsked.class));
    }

    @Test
    void theBracketsGoBackToTheLog() throws Exception {
        assertEquals(
                List.of(new ViewMsg.TabChosen(CommitsPanel.PANE, CommitsPane.LOG)),
                pressed(REFLOG, ']', ViewMsg.TabChosen.class));
    }

    /**
     * What pressing {@code key} in the commits pane of {@code model}, with the focus, sends of {@code kind}: a
     * {@link KeyCode} or a character.
     */
    private static List<Msg> pressed(Model model, Object key, Class<?> kind) throws Exception {
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        CommitsPanel panel = new CommitsPanel(sent::add, FILLS);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.PANE))) {
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.PANE));
            if (key instanceof KeyCode code) {
                SettledPilot.of(testRunner).press(code);
            } else {
                SettledPilot.of(testRunner).press((char) key);
            }
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }
        return List.ofAll(sent).filter(kind::isInstance);
    }
}
