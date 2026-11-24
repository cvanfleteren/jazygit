package net.vanfleteren.jazygit.features.changes.reflog.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPane;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogView;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.content.Heading;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import org.junit.jupiter.api.Test;

/**
 * What the highlighted entry of the reflog is, above its commit in the content panel (ADR 186, 187).
 */
class ReflogSourceTest {

    private static final ReflogSource SOURCE = new ReflogSource();
    private static final Model LOADED = TestModels.loaded(SampleData.git())
            .withNow(ReflogShownTest.CHECKOUT.time().plusSeconds(120));
    private static final ReflogView READ = ReflogView.NONE.withEntries(
            new Loadable.Loaded<>(List.of(ReflogShownTest.CHECKOUT, ReflogShownTest.COMMIT)));

    @Test
    void theHighlightedEntryIsSaidAboveItsCommit() {
        Model model = showing(READ.withHighlighted(Option.of(1)), ReflogShownTest.COMMIT.sha());

        assertEquals(
                List.of("HEAD@{1} · 2h ago", "commit: Add a", ""),
                texts(SOURCE.heading(model, Mirrored.COMMITS).get()));
    }

    @Test
    void aLostCommitIsSaidToBeLostAndOneHeadIsAtToBeKeptByHead() {
        Model lost = showing(
                READ.withHighlighted(Option.of(1))
                        .withKept(new Kept(HashSet.of(ReflogShownTest.COMMIT.sha()), HashSet.empty())),
                ReflogShownTest.COMMIT.sha());
        Model atHead = showing(READ.withHighlighted(Option.of(0)), ReflogShownTest.CHECKOUT.sha());
        Model atHeadOnly = showing(
                READ.withHighlighted(Option.of(0))
                        .withKept(new Kept(HashSet.empty(), HashSet.of(ReflogShownTest.CHECKOUT.sha()))),
                ReflogShownTest.CHECKOUT.sha());

        assertTrue(texts(SOURCE.heading(lost, Mirrored.COMMITS).get()).get(2).startsWith("✗ Lost"));
        assertEquals(
                "◆ HEAD is at this commit",
                texts(SOURCE.heading(atHead, Mirrored.COMMITS).get()).get(2));
        assertTrue(
                texts(SOURCE.heading(atHeadOnly, Mirrored.COMMITS).get()).get(2).contains("lost once HEAD moves"));
    }

    @Test
    void aCommitOnlyHeadReachesFromPastItIsSaidToBeKeptByHeadAlone() {
        Model byHead = showing(
                READ.withHighlighted(Option.of(1))
                        .withKept(new Kept(HashSet.empty(), HashSet.of(ReflogShownTest.COMMIT.sha()))),
                ReflogShownTest.COMMIT.sha());

        List<String> said = texts(SOURCE.heading(byHead, Mirrored.COMMITS).get());

        assertTrue(said.get(2).contains("only HEAD"), said.toString());
        assertTrue(said.forAll(line -> !line.contains("✗")), said.toString());
    }

    @Test
    void nothingIsSaidOverAnotherCommitAnotherPaneOrTheLog() {
        Model other = showing(READ.withHighlighted(Option.of(1)), ReflogShownTest.CHECKOUT.sha());
        Model shown = showing(READ.withHighlighted(Option.of(1)), ReflogShownTest.COMMIT.sha());
        Model log = shown.mapRepoView(view -> view.withTab(CommitsPanel.PANE, CommitsPane.LOG));
        Model none = showing(READ, ReflogShownTest.COMMIT.sha());

        assertEquals(Option.none(), SOURCE.heading(other, Mirrored.COMMITS));
        assertEquals(Option.none(), SOURCE.heading(shown, Mirrored.BRANCHES));
        assertEquals(Option.none(), SOURCE.heading(log, Mirrored.COMMITS));
        assertEquals(Option.none(), SOURCE.heading(none, Mirrored.COMMITS));
        assertEquals(Option.none(), SOURCE.shown(shown, Mirrored.COMMITS));
    }

    /**
     * {@link #LOADED} with the reflog, as {@code view}, in the commits pane, and the commit {@code sha} in the
     * content panel.
     */
    private static Model showing(ReflogView view, Sha sha) {
        return new SelectionMsg.CommitSelected(sha)
                .apply(LOADED.withPart(ReflogView.PART, view)
                        .mapRepoView(repo -> repo.withTab(CommitsPanel.PANE, ReflogView.TAB)))
                .model();
    }

    private static List<String> texts(Heading heading) {
        return heading.lines().map(parts -> parts.map(Heading.Part::text).mkString());
    }
}
