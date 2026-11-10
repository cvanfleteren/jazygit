package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;

class VersionTest {

    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    private static final DiffText CHANGES = DiffText.of(HEADER + "@@ -2,2 +2,2 @@\n two\n-three\n+THREE\n");
    /**
     * The file read whole: three lines, the last changed, and a fourth added at its end.
     */
    private static final DiffText WHOLE = DiffText.of(HEADER + "@@ -1,3 +1,4 @@\n one\n two\n-three\n+THREE\n+four\n");

    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(new Sha("5555"), CHANGES);

    @Test
    void itHoldsForTheSameChangesOfTheSameOneFile() {
        Version version = version(Option.of(FileVersion.NEW), Loadable.loading());

        assertTrue(version.holdsFor(new ScrolledContent.StashChanges(new Sha("5555"), CHANGES)));
        assertFalse(version.holdsFor(new ScrolledContent.StashChanges(new Sha("6666"), CHANGES)));
        assertFalse(version.holdsFor(new ScrolledContent.StashChanges(
                new Sha("5555"),
                DiffText.of(CHANGES.text() + HEADER.replace("a.txt", "b.txt") + "@@ -1 +1 @@\n-b\n+B\n"))));
    }

    @Test
    void aDiffOfTheWorkingTreeHoldsForAnotherDiffOfTheSameFile() {
        Version version =
                new Version(new ScrolledContent.Diff(CHANGES), "a.txt", Option.none(), read(), Loadable.loading());

        assertTrue(version.holdsFor(new ScrolledContent.Diff(DiffText.of(CHANGES.text() + " four\n"))));
        assertFalse(version.holdsFor(
                new ScrolledContent.Diff(DiffText.of(CHANGES.text().replace("a.txt", "b.txt")))));
    }

    @Test
    void theFileShownIsTheVersionOnceItIsRead() {
        Expansion.Folded diff = Expansion.Folded.of(CHANGES.parsed());

        assertSame(diff, version(Option.of(FileVersion.NEW), Loadable.loading()).applied(diff));

        Expansion.Folded shown = version(Option.of(FileVersion.NEW), new Loadable.Loaded<>(WHOLE))
                .applied(diff);

        assertEquals(
                List.of("one", "two", "THREE", "four"),
                shown.diff()
                        .files()
                        .head()
                        .hunks()
                        .head()
                        .lines()
                        .map(line -> line.line().text()));
        assertEquals(Expansion.Fold.of(FileVersion.NEW, false, 1), shown.fold("a.txt"));
        assertSame(diff, version(Option.none(), new Loadable.Loaded<>(WHOLE)).applied(diff));
    }

    @Test
    void aLineOfABlockThatEndsTheFileGoesToTheLastLineOfTheVersion() {
        Version version = version(Option.of(FileVersion.ORIGINAL), new Loadable.Loaded<>(WHOLE));

        assertEquals(
                new Anchor.OnLine("a.txt", Side.LEFT, 3),
                version.moved(new Anchor.OnLine("a.txt", Side.RIGHT, 4), Option.of(FileVersion.ORIGINAL)));
        // A line in both stays, and what is no line, or of another file, too.
        Anchor.OnLine two = new Anchor.OnLine("a.txt", Side.RIGHT, 2);
        assertEquals(two, version.moved(two, Option.of(FileVersion.ORIGINAL)));
        assertEquals(new Anchor.File("a.txt"), version.moved(new Anchor.File("a.txt"), Option.of(FileVersion.NEW)));
        Anchor.OnLine other = new Anchor.OnLine("b.txt", Side.LEFT, 1);
        assertEquals(other, version.moved(other, Option.of(FileVersion.NEW)));
    }

    @Test
    void aMarkerGoesAsTheLineItStartsAt() {
        Version version = version(Option.of(FileVersion.NEW), new Loadable.Loaded<>(WHOLE));

        assertEquals(
                new Anchor.OnLine("a.txt", Side.RIGHT, 1),
                version.moved(new Anchor.Gap("a.txt", 1, Option.of(2)), Option.of(FileVersion.NEW)));
    }

    @Test
    void aReadWithWhitespaceIgnoredIsMadeWithoutIt() {
        assertFalse(
                ReadVersion.of("a", CONTENT, read().source(), "a.txt", DiffOptions.DEFAULT.withIgnoreWhitespace(true))
                        .options()
                        .ignoreWhitespace());
    }

    private static Version version(Option<FileVersion> shown, Loadable<DiffText> whole) {
        return new Version(CONTENT, "a.txt", shown, read(), whole);
    }

    private static ReadVersion read() {
        return ReadVersion.of(
                "stash-changes",
                CONTENT,
                new ReadWholeFile.Source.OfStash(
                        new Sha("5555"), new CommitFile("a.txt", ChangeType.MODIFIED, Option.none())),
                "a.txt",
                DiffOptions.DEFAULT);
    }
}
