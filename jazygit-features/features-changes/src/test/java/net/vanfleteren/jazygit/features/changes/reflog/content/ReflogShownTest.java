package net.vanfleteren.jazygit.features.changes.reflog.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.CommitKind;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogMsg;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogView;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import org.junit.jupiter.api.Test;

/**
 * The reflog of HEAD as a tab of the commits pane (ADR 186): its rows, what an empty one says, and what highlighting
 * an entry asks for.
 */
class ReflogShownTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_000_000);
    private static final Model LOADED = TestModels.loaded(SampleData.git()).withNow(NOW);
    private static final Sha HEAD =
            LOADED.repo().status().toOption().get().head().commit().get();
    private static final Sha OTHER = new Sha("b".repeat(40));

    static final ReflogEntry CHECKOUT = entry(
            0,
            NOW.minusSeconds(120),
            "checkout: moving from main to feature-x",
            new Action.Checkout("main", "feature-x"),
            HEAD);
    static final ReflogEntry COMMIT =
            entry(1, NOW.minusSeconds(7_200), "commit: Add a", new Action.Committed(CommitKind.PLAIN, "Add a"), OTHER);

    @Test
    void aRowMarksAnEntryAtHeadAndShowsItsAgeActionSubjectAndSha() {
        assertEquals(
                "◆ 2m   checkout      main → feature-x " + HEAD.shortSha(),
                ReflogShown.row(CHECKOUT, Option.of(HEAD), false, NOW).text());
        assertEquals(
                "  2h   commit        Add a " + OTHER.shortSha(),
                ReflogShown.row(COMMIT, Option.of(HEAD), false, NOW).text());
    }

    @Test
    void aRowMarksAnEntryWhoseCommitIsLostUnlessHeadIsAtIt() {
        assertEquals(
                "✗ 2h   commit        Add a " + OTHER.shortSha(),
                ReflogShown.row(COMMIT, Option.of(HEAD), true, NOW).text());
        assertTrue(ReflogShown.row(CHECKOUT, Option.of(HEAD), true, NOW).text().startsWith("◆ "));
    }

    @Test
    void theSubjectIsMadeShort() {
        assertEquals("feature-x", ReflogShown.subject(new Action.Reset("feature-x")));
        assertEquals(
                "returning to main", ReflogShown.subject(new Action.Rebase("finish", "returning to refs/heads/main")));
        assertEquals("origin/main", ReflogShown.subject(new Action.Merge("origin/main", "Fast-forward")));
        assertEquals("clone: from x", ReflogShown.subject(new Action.Other("clone: from x")));
        assertEquals("rebase finish", ReflogShown.action(new Action.Rebase("finish", "")));
        assertEquals("pull", ReflogShown.action(new Action.Pull(Option.none(), "Fast-forward")));
        assertEquals("", ReflogShown.action(new Action.Other("x")));
    }

    @Test
    void anEmptyReflogSaysWhy() {
        assertTrue(ReflogShown.emptyText(ReflogView.NONE.withEmpty(Option.of(Reflog.Empty.OFF)))
                .exists(text -> text.contains("core.logAllRefUpdates")));
        assertTrue(ReflogShown.emptyText(ReflogView.NONE.withEmpty(Option.of(Reflog.Empty.NO_COMMITS)))
                .exists(text -> text.contains("No commits")));
        assertTrue(ReflogShown.emptyText(ReflogView.NONE).exists(text -> text.contains("empty")));
    }

    @Test
    void highlightingAnEntryShowsItsCommitThenSaysItIsHighlighted() {
        ReflogView view = ReflogView.NONE.withEntries(new Loadable.Loaded<>(List.of(CHECKOUT, COMMIT)));
        Model shown = LOADED.withPart(ReflogView.PART, view);
        Model showing = new SelectionMsg.CommitSelected(OTHER).apply(shown).model();

        assertEquals(
                Option.of(new SelectionMsg.CommitSelected(OTHER)),
                ReflogShown.selected(shown, view, Option.of(COMMIT)));
        assertEquals(Option.of(new ReflogMsg.Highlighted(1)), ReflogShown.selected(showing, view, Option.of(COMMIT)));
        assertEquals(
                Option.none(), ReflogShown.selected(showing, view.withHighlighted(Option.of(1)), Option.of(COMMIT)));
    }

    private static ReflogEntry entry(int place, Instant time, String subject, Action action, Sha sha) {
        return new ReflogEntry(
                place,
                time,
                subject,
                action,
                Fixtures.commit(sha, "Ada", "ada@example.com", Instant.EPOCH, "c", "", List.empty()));
    }
}
