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

        assertEquals(List.of(new ReadReflog(0, PAGE)), next.cmds());
        assertEquals(Option.of(new ReadReflog(0, PAGE)), view(next).reading());
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
        Model read = done(SHOWN, new ReadReflog(0, PAGE), entries(0, PAGE));
        Model further = done(read, new ReadReflog(PAGE, PAGE), entries(PAGE, 50));

        assertEquals(
                List.of(new ReadReflog(0, PAGE + 50)),
                ReflogUpdate.reacting(further, new KernelEvent.OperationDone(false))
                        .cmds());
        assertEquals(
                List.of(new ReadReflog(0, PAGE + 50)),
                ReflogUpdate.reacting(further, new KernelEvent.LogRead()).cmds());
        assertEquals(
                List.empty(),
                ReflogUpdate.reacting(further, new KernelEvent.Fetched()).cmds());
    }

    @Test
    void aFullFirstPageIsShownAndTheNextIsReadAtOnce() {
        Next next = read(reading(SHOWN, new ReadReflog(0, PAGE)), new ReadReflog(0, PAGE), entries(0, PAGE));

        assertEquals(entries(0, PAGE), view(next).entries().getOrElse(List.empty()));
        assertEquals(List.of(lookAt(1, true, entries(0, PAGE)), new ReadReflog(PAGE, PAGE)), next.cmds());
    }

    @Test
    void aPageShorterThanAskedForEndsTheReading() {
        Next next = read(reading(SHOWN, new ReadReflog(0, PAGE)), new ReadReflog(0, PAGE), entries(0, 3));

        assertTrue(view(next).ended());
        assertEquals(List.of(lookAt(1, true, entries(0, 3))), next.cmds());
        assertEquals(
                List.empty(),
                ReflogUpdate.update(next.model(), new ReflogMsg.Highlighted(2)).cmds());
    }

    @Test
    void theNextPageIsReadOnceTheHighlightEntersTheLastPageRead() {
        Model twoPages = done(
                done(SHOWN, new ReadReflog(0, PAGE), entries(0, PAGE)),
                new ReadReflog(PAGE, PAGE),
                entries(PAGE, PAGE));

        assertEquals(
                List.empty(),
                ReflogUpdate.update(twoPages, new ReflogMsg.Highlighted(PAGE - 1))
                        .cmds());
        assertEquals(
                List.of(new ReadReflog(2 * PAGE, PAGE)),
                ReflogUpdate.update(twoPages, new ReflogMsg.Highlighted(PAGE)).cmds());
    }

    @Test
    void aLaterPageIsAddedAfterThoseRead() {
        Model first = done(SHOWN, new ReadReflog(0, PAGE), entries(0, PAGE));

        Next next = read(reading(first, new ReadReflog(PAGE, PAGE)), new ReadReflog(PAGE, PAGE), entries(PAGE, 10));

        assertEquals(entries(0, PAGE + 10), view(next).entries().getOrElse(List.empty()));
    }

    @Test
    void aReadThatEndsOnceTheTabIsHiddenReadsNoFurther() {
        Model hidden = hide(reading(SHOWN, new ReadReflog(0, PAGE)));

        Next next = read(hidden, new ReadReflog(0, PAGE), entries(0, PAGE));

        assertEquals(entries(0, PAGE), view(next).entries().getOrElse(List.empty()));
        assertEquals(List.of(lookAt(1, true, entries(0, PAGE))), next.cmds());
        assertEquals(Option.none(), view(next).reading());
    }

    @Test
    void aRereadAskedForIsNotRunOnceTheTabIsHidden() {
        Model asked = ReflogUpdate.reacting(reading(SHOWN, new ReadReflog(0, PAGE)), new KernelEvent.LogRead())
                .model();

        Next next = read(hide(asked), new ReadReflog(0, PAGE), entries(0, 5));

        assertEquals(List.of(lookAt(1, true, entries(0, 5))), next.cmds());
        assertFalse(view(next).again());
    }

    @Test
    void askedToReadAgainWhileReadingItReadsAgainOnceDone() {
        Model running = reading(SHOWN, new ReadReflog(0, PAGE));

        Next asked = ReflogUpdate.reacting(running, new KernelEvent.LogRead());
        Next done = read(asked.model(), new ReadReflog(0, PAGE), entries(0, 5));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(List.of(lookAt(1, true, entries(0, 5)), new ReadReflog(0, PAGE)), done.cmds());
        assertFalse(view(done).again());
    }

    @Test
    void whatAReadNoLongerWantedReadIsDropped() {
        Model running = reading(SHOWN, new ReadReflog(0, PAGE));

        Next next = read(running, new ReadReflog(PAGE, PAGE), entries(PAGE, 5));

        assertEquals(running, next.model());
    }

    @Test
    void anEmptyReflogSaysWhy() {
        Next next = ReflogUpdate.update(
                reading(SHOWN, new ReadReflog(0, PAGE)), new ReflogMsg.Read(new ReadReflog(0, PAGE), Reflog.Empty.OFF));

        assertEquals(Option.of(Reflog.Empty.OFF), view(next).empty());
        assertEquals(new Loadable.Loaded<>(List.empty()), view(next).entries());
        assertTrue(view(next).ended());
    }

    @Test
    void aFirstReadThatFailedShowsTheFailureAndALaterOneEndsTheReading() {
        Failure failure = Failure.of(new GitError.UnexpectedOutput("x"));

        Next first = ReflogUpdate.update(
                reading(SHOWN, new ReadReflog(0, PAGE)), new ReflogMsg.Failed(new ReadReflog(0, PAGE), failure));
        Next later = ReflogUpdate.update(
                reading(done(SHOWN, new ReadReflog(0, PAGE), entries(0, PAGE)), new ReadReflog(PAGE, PAGE)),
                new ReflogMsg.Failed(new ReadReflog(PAGE, PAGE), failure));

        assertEquals(new Loadable.Failed<>(failure), view(first).entries());
        assertTrue(view(later).ended());
        assertEquals(entries(0, PAGE), view(later).entries().getOrElse(List.empty()));
    }

    @Test
    void theCommitsOfTheEntriesAreKnownToTheModel() {
        Model read = done(SHOWN, new ReadReflog(0, PAGE), entries(0, 3));

        assertEquals(
                Option.of(entries(0, 3).get(1).commit()),
                read.commit(entries(0, 3).get(1).sha()));
    }

    @Test
    void aLaterPageIsLookedAtForLostCommitsAloneAndAnEmptyOneNotAtAll() {
        Model first = done(SHOWN, new ReadReflog(0, PAGE), entries(0, PAGE));

        Next later = read(reading(first, new ReadReflog(PAGE, PAGE)), new ReadReflog(PAGE, PAGE), entries(PAGE, 10));
        Next none = read(reading(first, new ReadReflog(PAGE, PAGE)), new ReadReflog(PAGE, PAGE), List.empty());

        assertEquals(List.of(lookAt(2, false, entries(PAGE, 10))), later.cmds());
        assertEquals(List.empty(), none.cmds());
    }

    @Test
    void whatALookFoundLostIsMarkedAndALaterPageAddsToIt() {
        List<ReflogEntry> top = entries(0, PAGE);
        List<ReflogEntry> next = entries(PAGE, 10);
        Model first = found(done(SHOWN, new ReadReflog(0, PAGE), top), lookAt(1, true, top), top.get(3));

        Model both = found(done(first, new ReadReflog(PAGE, PAGE), next), lookAt(2, false, next), next.get(1));

        assertTrue(view(both).isLost(top.get(3)));
        assertTrue(view(both).isLost(next.get(1)));
        assertFalse(view(both).isLost(top.get(4)));
    }

    @Test
    void aLookAtAllTheEntriesReplacesWhatWasFoundBefore() {
        List<ReflogEntry> top = entries(0, 5);
        Model first = found(done(SHOWN, new ReadReflog(0, PAGE), top), lookAt(1, true, top), top.get(3));

        Model again = found(done(first, new ReadReflog(0, PAGE), top), lookAt(2, true, top), top.get(1));

        assertFalse(view(again).isLost(top.get(3)));
        assertTrue(view(again).isLost(top.get(1)));
    }

    @Test
    void anOlderLookThatEndsAfterANewerLookAtAllTheEntriesIsDropped() {
        List<ReflogEntry> top = entries(0, 5);
        Model newer = found(done(SHOWN, new ReadReflog(0, PAGE), top), lookAt(3, true, top), top.get(1));

        Model older = found(newer, lookAt(2, false, top), top.get(4));

        assertEquals(view(newer), view(older));
    }

    @Test
    void aLookThatFailedMarksNothing() {
        List<ReflogEntry> top = entries(0, 5);
        Model read = done(SHOWN, new ReadReflog(0, PAGE), top);

        Next next = ReflogUpdate.update(
                read, new ReflogMsg.LostUnknown(lookAt(1, true, top), Failure.of(new GitError.UnexpectedOutput("x"))));

        assertEquals(read, next.model());
    }

    @Test
    void theHighlightedEntryIsKnown() {
        List<ReflogEntry> top = entries(0, 5);

        Model highlighted = ReflogUpdate.update(done(SHOWN, new ReadReflog(0, PAGE), top), new ReflogMsg.Highlighted(2))
                .model();

        assertEquals(Option.of(top.get(2)), highlighted.part(ReflogView.PART).highlightedEntry());
    }

    private static FindLost lookAt(int read, boolean all, List<ReflogEntry> entries) {
        return new FindLost(read, all, entries.map(ReflogEntry::sha));
    }

    private static Model found(Model model, FindLost look, ReflogEntry... lost) {
        return ReflogUpdate.update(
                        model,
                        new ReflogMsg.LostFound(
                                look, HashSet.ofAll(List.of(lost).map(ReflogEntry::sha))))
                .model();
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
        Model read = read(reading(model, cmd), cmd, entries).model();
        return read.withPart(ReflogView.PART, read.part(ReflogView.PART).withReading(Option.none()));
    }

    private static Next read(Model model, ReadReflog cmd, List<ReflogEntry> entries) {
        return ReflogUpdate.update(model, new ReflogMsg.Read(cmd, new Reflog.Entries(entries)));
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
