package net.vanfleteren.jazygit.features.changes.reflog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPane;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Reading the reflog of HEAD while its tab is shown, a page ahead of the highlight, one read at a time (ADR 186).
 */
class ReflogUpdateTest {

    private static final int PAGE = History.REFLOG_PAGE;
    private static final Model HIDDEN = TestModels.loaded(SampleData.git());
    private static final Model SHOWN = HIDDEN.mapRepoView(view -> view.withTab(CommitsPanel.PANE, ReflogView.TAB));

    @Test
    void showingTheTabReadsTheFirstPage() {
        Next next = ReflogUpdate.reacting(HIDDEN, new KernelEvent.TabShown(CommitsPanel.PANE, ReflogView.TAB));

        assertEquals(List.of(readOf(0, PAGE)), next.cmds());
        assertEquals(Option.of(readOf(0, PAGE)), view(next).reading());
    }

    @Test
    void anotherTabShownReadsNothing() {
        Next next = ReflogUpdate.reacting(HIDDEN, new KernelEvent.TabShown(CommitsPanel.PANE, CommitsPane.LOG));

        assertEquals(List.empty(), next.cmds());
        assertEquals(HIDDEN, next.model());
    }

    @Test
    void whileHiddenTheReflogIsNotReadAgain() {
        List<KernelEvent> events = List.of(
                new KernelEvent.LogRead(), new KernelEvent.OperationDone(false), new KernelEvent.RepositoryOpened());

        assertEquals(
                List.of(List.empty(), List.empty(), List.empty()),
                events.map(event -> ReflogUpdate.reacting(HIDDEN, event).cmds()));
    }

    @Test
    void whileShownItIsReadAgainAfterAnOperationOrTheLogAsFarAsItWasRead() {
        Model read = done(SHOWN, readOf(0, PAGE), entries(0, PAGE));
        Model further = done(read, readOf(PAGE, PAGE), entries(PAGE, 50));

        assertEquals(
                List.of(readOf(0, PAGE + 50)),
                ReflogUpdate.reacting(further, new KernelEvent.OperationDone(false))
                        .cmds());
        assertEquals(
                List.of(readOf(0, PAGE + 50)),
                ReflogUpdate.reacting(further, new KernelEvent.LogRead()).cmds());
        assertEquals(
                List.empty(),
                ReflogUpdate.reacting(further, new KernelEvent.Fetched()).cmds());
    }

    @Test
    void aFullFirstPageIsShownAndTheNextIsReadAtOnce() {
        Next next = read(reading(SHOWN, readOf(0, PAGE)), readOf(0, PAGE), entries(0, PAGE));

        assertEquals(entries(0, PAGE), view(next).entries().getOrElse(List.empty()));
        assertEquals(List.of(readOf(PAGE, PAGE)), next.cmds());
    }

    @Test
    void aPageShorterThanAskedForEndsTheReading() {
        Next next = read(reading(SHOWN, readOf(0, PAGE)), readOf(0, PAGE), entries(0, 3));

        assertTrue(view(next).ended());
        assertEquals(List.empty(), next.cmds());
        assertEquals(
                List.empty(),
                ReflogUpdate.update(next.model(), new ReflogMsg.Highlighted(2)).cmds());
    }

    @Test
    void theNextPageIsReadOnceTheHighlightEntersTheLastPageRead() {
        Model twoPages = done(done(SHOWN, readOf(0, PAGE), entries(0, PAGE)), readOf(PAGE, PAGE), entries(PAGE, PAGE));

        assertEquals(
                List.empty(),
                ReflogUpdate.update(twoPages, new ReflogMsg.Highlighted(PAGE - 1))
                        .cmds());
        assertEquals(
                List.of(readOf(2 * PAGE, PAGE)),
                ReflogUpdate.update(twoPages, new ReflogMsg.Highlighted(PAGE)).cmds());
    }

    @Test
    void aLaterPageIsAddedAfterThoseRead() {
        Model first = done(SHOWN, readOf(0, PAGE), entries(0, PAGE));

        Next next = read(reading(first, readOf(PAGE, PAGE)), readOf(PAGE, PAGE), entries(PAGE, 10));

        assertEquals(entries(0, PAGE + 10), view(next).entries().getOrElse(List.empty()));
    }

    @Test
    void aReadThatEndsOnceTheTabIsHiddenReadsNoFurther() {
        Model hidden = hide(reading(SHOWN, readOf(0, PAGE)));

        Next next = read(hidden, readOf(0, PAGE), entries(0, PAGE));

        assertEquals(entries(0, PAGE), view(next).entries().getOrElse(List.empty()));
        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.none(), view(next).reading());
    }

    @Test
    void aRereadAskedForIsNotRunOnceTheTabIsHidden() {
        Model asked = ReflogUpdate.reacting(reading(SHOWN, readOf(0, PAGE)), new KernelEvent.LogRead())
                .model();

        Next next = read(hide(asked), readOf(0, PAGE), entries(0, 5));

        assertEquals(List.empty(), next.cmds());
        assertFalse(view(next).again());
    }

    @Test
    void askedToReadAgainWhileReadingItReadsAgainOnceDone() {
        Model running = reading(SHOWN, readOf(0, PAGE));

        Next asked = ReflogUpdate.reacting(running, new KernelEvent.LogRead());
        Next done = read(asked.model(), readOf(0, PAGE), entries(0, 5));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(List.of(readOf(0, PAGE)), done.cmds());
        assertFalse(view(done).again());
    }

    @Test
    void whatAReadNoLongerWantedReadIsDropped() {
        Model running = reading(SHOWN, readOf(0, PAGE));

        Next next = read(running, readOf(PAGE, PAGE), entries(PAGE, 5));

        assertEquals(running, next.model());
    }

    @Test
    void anEmptyReflogSaysWhy() {
        Next next = ReflogUpdate.update(
                reading(SHOWN, readOf(0, PAGE)),
                new ReflogMsg.Read(readOf(0, PAGE), Reflog.Empty.OFF, Option.of(Kept.BY_REFS)));

        assertEquals(Option.of(Reflog.Empty.OFF), view(next).empty());
        assertEquals(new Loadable.Loaded<>(List.empty()), view(next).entries());
        assertTrue(view(next).ended());
    }

    @Test
    void aFirstReadThatFailedShowsTheFailureAndALaterOneEndsTheReading() {
        Failure failure = Failure.of(new GitError.UnexpectedOutput("x"));

        Next first =
                ReflogUpdate.update(reading(SHOWN, readOf(0, PAGE)), new ReflogMsg.Failed(readOf(0, PAGE), failure));
        Next later = ReflogUpdate.update(
                reading(done(SHOWN, readOf(0, PAGE), entries(0, PAGE)), readOf(PAGE, PAGE)),
                new ReflogMsg.Failed(readOf(PAGE, PAGE), failure));

        assertEquals(new Loadable.Failed<>(failure), view(first).entries());
        assertTrue(view(later).ended());
        assertEquals(entries(0, PAGE), view(later).entries().getOrElse(List.empty()));
    }

    @Test
    void theCommitsOfTheEntriesAreKnownToTheModel() {
        Model read = done(SHOWN, readOf(0, PAGE), entries(0, 3));

        assertEquals(
                Option.of(entries(0, 3).get(1).commit()),
                read.commit(entries(0, 3).get(1).sha()));
    }

    @Test
    void whatKeepsTheCommitsComesWithTheEntriesAndALaterPageAddsToIt() {
        List<ReflogEntry> top = entries(0, PAGE);
        List<ReflogEntry> later = entries(PAGE, 10);
        Model first = done(SHOWN, readOf(0, PAGE), top, kept(List.of(top.get(3)), List.of(top.get(4))));

        Model both = done(first, readOf(PAGE, PAGE), later, kept(List.of(later.get(1)), List.empty()));

        assertTrue(view(both).isLost(top.get(3)));
        assertTrue(view(both).isKeptByHead(top.get(4)));
        assertTrue(view(both).isLost(later.get(1)));
        assertFalse(view(both).isLost(top.get(5)));
        assertFalse(view(both).isKeptByHead(top.get(5)));
    }

    @Test
    void aReadFromTheTopReplacesWhatWasKnown() {
        List<ReflogEntry> top = entries(0, 5);
        Model first = done(SHOWN, readOf(0, PAGE), top, kept(List.of(top.get(3)), List.empty()));

        Model again = done(first, readOf(0, PAGE), top, kept(List.of(top.get(1)), List.empty()));

        assertFalse(view(again).isLost(top.get(3)));
        assertTrue(view(again).isLost(top.get(1)));
    }

    /**
     * What keeps the commits comes with the read it is of, so a read that is no longer wanted brings none of it: no
     * look at them can end after a newer one and undo it.
     */
    @Test
    void whatKeepsTheCommitsOfAReadNoLongerWantedIsDroppedWithIt() {
        List<ReflogEntry> top = entries(0, 5);
        Model running = reading(SHOWN, readOf(0, PAGE));

        Next stale = read(running, readOf(PAGE, PAGE), entries(PAGE, 5), kept(List.of(top.get(1)), List.empty()));

        assertEquals(running, stale.model());
    }

    @Test
    void whenWhatKeepsTheCommitsIsNotKnownTheEntriesShowUnmarked() {
        List<ReflogEntry> top = entries(0, 5);
        Model first = done(SHOWN, readOf(0, PAGE), top, kept(List.of(top.get(1)), List.empty()));

        Model unknown = ReflogUpdate.update(
                        reading(first, readOf(0, PAGE)),
                        new ReflogMsg.Read(readOf(0, PAGE), new Reflog.Entries(top), Option.none()))
                .model();

        assertEquals(top, view(unknown).entries().getOrElse(List.empty()));
        assertFalse(view(unknown).isLost(top.get(1)));
    }

    @Test
    void theHighlightedEntryIsKnown() {
        List<ReflogEntry> top = entries(0, 5);

        Model highlighted = ReflogUpdate.update(done(SHOWN, readOf(0, PAGE), top), new ReflogMsg.Highlighted(2))
                .model();

        assertEquals(Option.of(top.get(2)), highlighted.part(ReflogView.PART).highlightedEntry());
    }

    private static ReadReflog readOf(int skip, int max) {
        return new ReadReflog(skip, max);
    }

    private static Kept kept(List<ReflogEntry> lost, List<ReflogEntry> byHead) {
        return new Kept(HashSet.ofAll(lost.map(ReflogEntry::sha)), HashSet.ofAll(byHead.map(ReflogEntry::sha)));
    }

    private static Model hide(Model model) {
        return model.mapRepoView(view -> view.withTab(CommitsPanel.PANE, CommitsPane.LOG));
    }

    private static Model reading(Model model, ReadReflog cmd) {
        return model.withPart(ReflogView.PART, model.part(ReflogView.PART).withReading(Option.of(cmd)));
    }

    /**
     * {@code model} once {@code cmd} read {@code entries}, without what that asks for.
     */
    private static Model done(Model model, ReadReflog cmd, List<ReflogEntry> entries) {
        return done(model, cmd, entries, Kept.BY_REFS);
    }

    private static Model done(Model model, ReadReflog cmd, List<ReflogEntry> entries, Kept kept) {
        Model read = read(reading(model, cmd), cmd, entries, kept).model();
        return read.withPart(ReflogView.PART, read.part(ReflogView.PART).withReading(Option.none()));
    }

    private static Next read(Model model, ReadReflog cmd, List<ReflogEntry> entries) {
        return read(model, cmd, entries, Kept.BY_REFS);
    }

    private static Next read(Model model, ReadReflog cmd, List<ReflogEntry> entries, Kept kept) {
        return ReflogUpdate.update(model, new ReflogMsg.Read(cmd, new Reflog.Entries(entries), Option.of(kept)));
    }

    private static ReflogView view(Next next) {
        return view(next.model());
    }

    private static ReflogView view(Model model) {
        return model.part(ReflogView.PART);
    }

    /**
     * {@code count} entries from the place {@code from} on, each at a commit of its own.
     */
    static List<ReflogEntry> entries(int from, int count) {
        return List.range(from, from + count).map(ReflogUpdateTest::entry);
    }

    static ReflogEntry entry(int place) {
        Sha sha = new Sha("%040x".formatted(place + 1));
        return new ReflogEntry(
                place,
                Instant.ofEpochSecond(100_000L - place),
                "commit: c" + place,
                new Action.Committed(ReflogEntry.CommitKind.PLAIN, "c" + place),
                Fixtures.commit(sha, "Ada", "ada@example.com", Instant.EPOCH, "c" + place, "", List.empty()));
    }
}
