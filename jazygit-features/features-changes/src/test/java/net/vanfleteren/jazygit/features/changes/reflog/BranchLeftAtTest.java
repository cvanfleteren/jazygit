package net.vanfleteren.jazygit.features.changes.reflog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.CommitKind;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import org.junit.jupiter.api.Test;

/**
 * Which branch HEAD left at an entry of the reflog, as the entry newer than it says (ADR 194).
 */
class BranchLeftAtTest {

    @Test
    void theEntryBeforeACheckoutIsWhereTheBranchItLeftStood() {
        ReflogView view = view(checkout(0, "feature-x", "main"), commit(1), commit(2));

        assertEquals(Option.of(new BranchName("feature-x")), view.branchLeftAt(at(view, 1)));
        assertEquals(Option.none(), view.branchLeftAt(at(view, 0)));
        assertEquals(Option.none(), view.branchLeftAt(at(view, 2)));
    }

    @Test
    void aCheckoutFromADetachedHeadOrARevisionLeftNoBranch() {
        ReflogView detached = view(checkout(0, "0cca89e3b2f1a8d0c4e5f6a7b8c9d0e1f2a3b4c5", "main"), commit(1));
        ReflogView revision = view(checkout(0, "HEAD~1", "main"), commit(1));

        assertEquals(Option.none(), detached.branchLeftAt(at(detached, 1)));
        assertEquals(Option.none(), revision.branchLeftAt(at(revision, 1)));
    }

    @Test
    void aNameReadsAsABranchOnlyWhenGitAllowsItForOne() {
        assertTrue(ReflogView.readsAsBranch("main"));
        assertTrue(ReflogView.readsAsBranch("feature/login"));
        assertTrue(ReflogView.readsAsBranch("fix-12"));
        // All hex, as a date or a ticket number: git names a detached HEAD by its whole id only.
        assertTrue(ReflogView.readsAsBranch("20261124"));
        assertTrue(ReflogView.readsAsBranch("deadbeef"));
        List.of(
                        "HEAD",
                        "0cca89e3b2f1a8d0c4e5f6a7b8c9d0e1f2a3b4c5",
                        "a".repeat(64),
                        "main^",
                        "main~2",
                        "HEAD@{1}",
                        "a..b",
                        "x.lock",
                        "x/",
                        "x.",
                        ".x",
                        "a:b",
                        "a*")
                .forEach(name -> assertFalse(ReflogView.readsAsBranch(name), name));
    }

    private static ReflogView view(ReflogEntry... entries) {
        return ReflogView.NONE.withEntries(new Loadable.Loaded<>(List.of(entries)));
    }

    private static ReflogEntry at(ReflogView view, int place) {
        return view.entries().getOrElse(List.empty()).get(place);
    }

    private static ReflogEntry checkout(int place, String from, String to) {
        return entry(place, "checkout: moving from " + from + " to " + to, new Action.Checkout(from, to));
    }

    private static ReflogEntry commit(int place) {
        return entry(place, "commit: c" + place, new Action.Committed(CommitKind.PLAIN, "c" + place));
    }

    private static ReflogEntry entry(int place, String subject, Action action) {
        return new ReflogEntry(
                place,
                Instant.ofEpochSecond(1_000 - place),
                subject,
                action,
                Fixtures.commit(
                        new Sha("%040x".formatted(place + 1)),
                        "Ada",
                        "ada@example.com",
                        Instant.EPOCH,
                        "c",
                        "",
                        List.empty()));
    }
}
