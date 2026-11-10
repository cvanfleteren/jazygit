package net.vanfleteren.jazygit.feature.diff;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.ReadVersion;
import net.vanfleteren.jazygit.state.ReadWholeFile;
import net.vanfleteren.jazygit.state.ReadWholeFile.Source;
import net.vanfleteren.jazygit.state.Scrolled;
import net.vanfleteren.jazygit.state.ScrolledContent;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Version;
import org.junit.jupiter.api.Test;

class VersionUpdateTest {

    private static final String AREA = "stash-changes";
    private static final Sha STASH = new Sha("5555");
    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    /**
     * Line 10 of a file of 30 changed, as git shows it with three lines of context.
     */
    private static final DiffText CHANGES = DiffText.of(HEADER + "@@ -7,7 +7,7 @@\n"
            + List.rangeClosed(7, 13).map(VersionUpdateTest::line).mkString());
    /**
     * The same change, read with the whole file as context.
     */
    private static final DiffText WHOLE = DiffText.of(HEADER + "@@ -1,30 +1,30 @@\n"
            + List.rangeClosed(1, 30).map(VersionUpdateTest::line).mkString());

    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(STASH, CHANGES);
    private static final Source SOURCE =
            new Source.OfStash(STASH, new CommitFile("a.txt", ChangeType.MODIFIED, Option.none()));
    private static final Anchor.OnLine REMOVED = new Anchor.OnLine("a.txt", Side.LEFT, 10);
    private static final Anchor.OnLine ADDED = new Anchor.OnLine("a.txt", Side.RIGHT, 10);

    @Test
    void theFirstTimeTheFileIsReadWholeWithWhitespaceNotIgnored() {
        Model model =
                Update.update(loaded(), new DiffOptionsMsg.WhitespaceToggled()).model();
        assertTrue(model.preferences().diffOptions().ignoreWhitespace());

        Next next = Update.update(model, new VersionMsg.Cycled(at(REMOVED)));

        ReadVersion read = read(model);
        assertFalse(read.options().ignoreWhitespace());
        assertEquals(List.of(read), next.cmds());
        Version version = version(next.model()).get();
        assertEquals(Option.of(FileVersion.NEW), version.shown());
        assertEquals(Loadable.loading(), version.whole());
        assertEquals(Option.none(), version.showing());
    }

    @Test
    void onceReadTheCursorGoesFromARemovedLineToTheLineAfterItsBlock() {
        Model shown = readWhole(Update.update(loaded(), new VersionMsg.Cycled(at(REMOVED))));

        assertEquals(Option.of(FileVersion.NEW), version(shown).flatMap(Version::showing));
        assertEquals(Option.of(ADDED), shown.scrolled().get(AREA).flatMap(Scrolled::cursor));
    }

    @Test
    void theCycleGoesOnToTheOriginalAndBackToTheDiffWithoutReadingAgain() {
        Model shown = readWhole(Update.update(loaded(), new VersionMsg.Cycled(at(ADDED))));

        Next original = Update.update(shown, new VersionMsg.Cycled(at(ADDED)));
        assertEquals(List.of(), original.cmds());
        assertEquals(Option.of(FileVersion.ORIGINAL), version(original.model()).flatMap(Version::showing));
        // An added line goes to the line after its block in the original, numbered on the right as a context line.
        Anchor.OnLine after = new Anchor.OnLine("a.txt", Side.RIGHT, 11);
        assertEquals(Option.of(after), original.model().scrolled().get(AREA).flatMap(Scrolled::cursor));

        Next diff = Update.update(original.model(), new VersionMsg.Cycled(at(after)));
        assertEquals(List.of(), diff.cmds());
        assertEquals(Option.none(), version(diff.model()).flatMap(Version::shown));
        assertEquals(Option.of(after), diff.model().scrolled().get(AREA).flatMap(Scrolled::cursor));
    }

    @Test
    void aFileReadWholeToExpandItWithWhitespaceNotIgnoredIsNotReadAgain() {
        Next expanded = Update.update(
                loaded(), new ExpandMsg.Asked(at(new Anchor.Gap("a.txt", 1, Option.of(7))), ExpandMsg.Toward.ALL));
        ReadWholeFile whole = (ReadWholeFile) expanded.cmds().head();
        Model read = TestModels.done(
                        TestModels.running(expanded.model(), whole), whole, new LoadMsg.WholeFileRead(whole, WHOLE))
                .model();

        Next next = Update.update(read, new VersionMsg.Cycled(at(REMOVED)));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.of(FileVersion.NEW), version(next.model()).flatMap(Version::showing));
        assertEquals(Option.of(ADDED), next.model().scrolled().get(AREA).flatMap(Scrolled::cursor));
    }

    @Test
    void anAreaOfSeveralFilesShowsNoVersion() {
        DiffText two = DiffText.of(CHANGES.text() + HEADER.replace("a.txt", "b.txt") + "@@ -1 +1 @@\n-b\n+B\n");
        Model model = loaded();
        DiffCursorAt at = new DiffCursorAt(
                AREA,
                new Scrolled(new ScrolledContent.StashChanges(STASH, two), 0, 0, Option.of(ADDED), Option.none()),
                ADDED,
                Option.none(),
                Option.none(),
                Option.none());

        Next next = Update.update(model, new VersionMsg.Cycled(at));

        assertSame(model, next.model());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void anotherItemHighlightedDropsTheVersion() {
        Model shown = readWhole(Update.update(loaded(), new VersionMsg.Cycled(at(REMOVED))));

        Model other = Update.update(shown, new SelectionMsg.StashSelected(new Sha("6666")))
                .model();

        assertTrue(other.repoView().versions().isEmpty());
    }

    /**
     * The cursor on {@code anchor} in the changes of the stash.
     */
    private static DiffCursorAt at(Anchor anchor) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.of(anchor), Option.none()),
                anchor,
                Option.none(),
                Option.none(),
                Option.none());
    }

    /**
     * How the file is read whole from the stash for a version, with the options of {@code model}.
     */
    private static ReadVersion read(Model model) {
        return ReadVersion.of(
                AREA, CONTENT, SOURCE, "a.txt", model.preferences().diffOptions());
    }

    /**
     * {@code asked} once the file it reads whole is read.
     */
    private static Model readWhole(Next asked) {
        ReadVersion read = (ReadVersion) asked.cmds().head();
        return TestModels.done(TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, WHOLE))
                .model();
    }

    private static Option<Version> version(Model model) {
        return model.repoView().versions().get(AREA);
    }

    private static String line(int number) {
        return number == 10 ? "-line10\n+changed\n" : " line" + number + "\n";
    }
}
