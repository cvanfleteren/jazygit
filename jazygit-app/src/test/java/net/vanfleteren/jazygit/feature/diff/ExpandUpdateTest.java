package net.vanfleteren.jazygit.feature.diff;

import static net.vanfleteren.jazygit.state.TestModels.DIRTY;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Cmd.ReadWholeFile.Source;
import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.Expansion;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Scrolled;
import net.vanfleteren.jazygit.state.ScrolledContent;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class ExpandUpdateTest {

    private static final String AREA = "stash-changes";
    private static final Sha STASH = new Sha("5555");
    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    /**
     * Line 10 of a file of 30 changed, as git shows it with three lines of context.
     */
    private static final DiffText CHANGES = DiffText.of(HEADER + "@@ -7,7 +7,7 @@\n"
            + List.rangeClosed(7, 13).map(ExpandUpdateTest::line).mkString());
    /**
     * The same change, read with the whole file as context.
     */
    private static final DiffText WHOLE = DiffText.of(HEADER + "@@ -1,30 +1,30 @@\n"
            + List.rangeClosed(1, 30).map(ExpandUpdateTest::line).mkString());

    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(STASH, CHANGES);
    private static final Anchor.Gap ABOVE = new Anchor.Gap("a.txt", 1, Option.of(7));
    private static final Anchor.Gap BELOW = new Anchor.Gap("a.txt", 14, Option.none());

    @Test
    void theFirstExpansionInAFileReadsItWholeAsItsDiffWasRead() {
        Model model = loaded();

        Next next = Update.update(model, new ExpandMsg.Asked(at(ABOVE), ExpandMsg.Toward.ALL));

        Cmd.ReadWholeFile read = read(model);
        assertEquals(List.of(read), next.cmds());
        assertEquals(
                Option.of(new Expansion.Expanded(read, Loadable.loading(), List.of(new Folding.Lines(1, 6)))),
                expanded(next.model()));
    }

    @Test
    void anotherExpansionInAFileReadWholeReadsNothingAndShowsMore() {
        Model read = readWhole(Update.update(loaded(), new ExpandMsg.Asked(at(ABOVE), ExpandMsg.Toward.ALL)));

        Next next = Update.update(read, new ExpandMsg.Asked(at(BELOW), ExpandMsg.Toward.DOWN));

        assertEquals(List.of(), next.cmds());
        Expansion expansion = next.model().repoView().expanded().get(AREA).get();
        assertEquals(
                List.of("@@ -1,23 +1,23 @@"),
                expansion
                        .folded(CHANGES.parsed(), next.model().preferences().context())
                        .diff()
                        .files()
                        .head()
                        .hunks()
                        .map(hunk -> hunk.hunk().header()));
        assertEquals(
                new Expansion.Fold(Option.of(30), false, false),
                expansion
                        .folded(CHANGES.parsed(), next.model().preferences().context())
                        .fold("a.txt"));
    }

    @Test
    void aFileThatCouldNotBeReadWholeIsReadAgainOnTheNextExpansion() {
        Next asked = Update.update(loaded(), new ExpandMsg.Asked(at(ABOVE), ExpandMsg.Toward.ALL));
        Cmd.ReadWholeFile read = read(loaded());
        Model failed = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.Failed(read, refused("boom")))
                .model();
        assertTrue(expanded(failed).get().whole().isFailed());

        Next again = Update.update(failed, new ExpandMsg.Asked(at(BELOW), ExpandMsg.Toward.DOWN));

        assertEquals(List.of(read), again.cmds());
        assertEquals(
                List.of(new Folding.Lines(1, 6), new Folding.Lines(14, 23)),
                expanded(again.model()).get().lines());
    }

    @Test
    void changingTheOptionsDropsWhatWasExpanded() {
        Model read = readWhole(Update.update(loaded(), new ExpandMsg.Asked(at(ABOVE), ExpandMsg.Toward.ALL)));

        Model wider = Update.update(read, new DiffOptionsMsg.ContextWidened()).model();

        assertTrue(wider.repoView().expanded().isEmpty());
    }

    @Test
    void aKeyOfTheMarkersOffAMarkerDoesNothing() {
        Model model = loaded();
        DiffCursorAt onFile = new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.none(), Option.none()),
                new Anchor.File("a.txt"),
                Option.none(),
                Option.none(),
                Option.none());

        Next next = Update.update(model, new ExpandMsg.Asked(onFile, ExpandMsg.Toward.ALL));

        assertSame(model, next.model());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theKeysShowTenLinesBelowTheHunkAboveTenAboveTheHunkBelowOrAll() {
        Anchor.Gap between = new Anchor.Gap("a.txt", 20, Option.of(100));

        assertEquals(new Folding.Lines(20, 29), ExpandUpdate.lines(between, ExpandMsg.Toward.DOWN));
        assertEquals(new Folding.Lines(90, 99), ExpandUpdate.lines(between, ExpandMsg.Toward.UP));
        assertEquals(new Folding.Lines(20, 99), ExpandUpdate.lines(between, ExpandMsg.Toward.ALL));
        assertEquals(new Folding.Lines(1, 6), ExpandUpdate.lines(ABOVE, ExpandMsg.Toward.UP));
        assertEquals(new Folding.Lines(14, Integer.MAX_VALUE), ExpandUpdate.lines(BELOW, ExpandMsg.Toward.ALL));
    }

    @Test
    void aFileOfTheWorkingTreeIsReadWholeFromItsSideAndAgainWhenItsDiffIs() {
        DiffText unstaged = DiffText.of(HEADER.replace("--- a/a.txt", "--- /dev/null") + "@@ -0,0 +1 @@\n+a\n");
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files()))
                .model();
        model = Update.update(
                        model,
                        new LoadMsg.FileDiffLoaded(
                                DIRTY.files(), model.preferences().diffOptions(), new Diffs("", unstaged.text())))
                .model();
        ScrolledContent shown = new ScrolledContent.Diff(unstaged);
        DiffCursorAt at = new DiffCursorAt(
                "diff-unstaged",
                new Scrolled(shown, 0, 0, Option.none(), Option.none()),
                new Anchor.Gap("a.txt", 2, Option.none()),
                Option.none(),
                Option.none(),
                Option.none());

        Next asked = Update.update(model, new ExpandMsg.Asked(at, ExpandMsg.Toward.DOWN));

        Cmd.ReadWholeFile read = new Cmd.ReadWholeFile(
                "diff-unstaged",
                shown,
                new Source.WorkingTree(DIRTY.files().head(), false),
                "a.txt",
                model.preferences().diffOptions());
        assertEquals(List.of(read), asked.cmds());
        Model whole = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.WholeFileRead(read, unstaged))
                .model();

        Next reread = Update.update(
                whole,
                new LoadMsg.FileDiffLoaded(
                        DIRTY.files(), whole.preferences().diffOptions(), new Diffs("", unstaged.text() + "+b\n")));

        assertTrue(reread.cmds().contains(read), reread.cmds().mkString("\n"));
    }

    /**
     * The cursor on {@code gap} in the changes of the stash.
     */
    private static DiffCursorAt at(Anchor.Gap gap) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.of(gap), Option.none()),
                gap,
                Option.none(),
                Option.none(),
                Option.none());
    }

    /**
     * How the file is read whole from the stash, with the options of {@code model}.
     */
    private static Cmd.ReadWholeFile read(Model model) {
        return new Cmd.ReadWholeFile(
                AREA,
                CONTENT,
                new Source.OfStash(STASH, new CommitFile("a.txt", ChangeType.MODIFIED, Option.none())),
                "a.txt",
                model.preferences().diffOptions());
    }

    /**
     * {@code asked} once the file it reads whole is read.
     */
    private static Model readWhole(Next asked) {
        Cmd.ReadWholeFile read = (Cmd.ReadWholeFile) asked.cmds().head();
        return TestModels.done(TestModels.running(asked.model(), read), read, new LoadMsg.WholeFileRead(read, WHOLE))
                .model();
    }

    private static Option<Expansion.Expanded> expanded(Model model) {
        return model.repoView()
                .expanded()
                .get(AREA)
                .flatMap(expansion -> expansion.files().get("a.txt"));
    }

    private static String line(int number) {
        return number == 10 ? "-line10\n+changed\n" : " line" + number + "\n";
    }
}
