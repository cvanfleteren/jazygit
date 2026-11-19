package net.vanfleteren.jazygit.kernel.content.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.core.git.model.DiffFilter;
import net.vanfleteren.jazygit.core.git.model.DiffFiltering.HunkStart;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.Expansion;
import net.vanfleteren.jazygit.kernel.state.diff.Filtering;
import net.vanfleteren.jazygit.kernel.state.diff.Opened;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The hunks the diff filters hide, as the rich view and the split one draw them (ADR 144).
 */
class FilteredDiffTest {

    private static final DiffFilter IMPORTS = DiffFilter.of(
                    "java-imports",
                    Option.of("Java imports"),
                    List.of("java"),
                    List.empty(),
                    Option.of(List.of("^import ")))
            .get();

    private static final ParsedDiff DIFF = ParsedDiff.of("""
            diff --git a/A.java b/A.java
            --- a/A.java
            +++ b/A.java
            @@ -1,4 +1,4 @@
             package a;
            -import a.B;
            +import a.C;

            @@ -20,3 +20,3 @@
             x
            -int a;
            +int b;
            @@ -40,3 +40,4 @@
             y
            +import a.D;
            +
             z
            diff --git a/b.txt b/b.txt
            --- a/b.txt
            +++ b/b.txt
            @@ -1 +1 @@
            -old
            +new
            """);

    private static final ParsedDiff IMPORTS_ONLY = ParsedDiff.of("""
            diff --git a/A.java b/A.java
            --- a/A.java
            +++ b/A.java
            @@ -1,3 +1,3 @@
             package a;
            -import a.B;
            +import a.C;
            diff --git a/b.txt b/b.txt
            --- a/b.txt
            +++ b/b.txt
            @@ -1 +1 @@
            -old
            +new
            """);

    @Test
    void aHunkHiddenIsAMarkerThatSaysWhatHidesIt() {
        List<String> lines = text(drawn(filtering(), DIFF, false));

        assertEquals(
                List.of(
                        " modified  A.java",
                        "┄┄┄┄┄ │ ┄ 2 filtered changes · Java imports ┄",
                        "┄┄┄┄┄ │ 15 lines hidden ┄",
                        "20 20 │   x",
                        "21    │ - int a;",
                        "   21 │ + int b;",
                        "┄┄┄┄┄ │ 17 lines hidden ┄",
                        "┄┄┄┄┄ │ ┄ 2 filtered changes · Java imports ┄",
                        "┄┄┄┄┄ │ more ┄",
                        "",
                        " modified  b.txt",
                        "1   │ - old",
                        "  1 │ + new",
                        "┄┄┄ │ more ┄"),
                lines.map(String::stripTrailing));
    }

    @Test
    void aMarkerIsAStopByItsHunks() {
        Drawn drawn = drawn(filtering(), DIFF, false);

        assertEquals(
                List.of(List.of(new HunkStart(1, 1)), List.of(new HunkStart(40, 40))),
                drawn.stops()
                        .map(stop -> stop.mark().anchor())
                        .filter(Anchor.Filtered.class::isInstance)
                        .map(anchor -> ((Anchor.Filtered) anchor).starts()));
    }

    @Test
    void aHunkOpenedShowsUnderAMarkerThatHidesItAgain() {
        Opened opened = new Opened(new NoDiff(), HashSet.of(new Opened.Hunk("A.java", new HunkStart(1, 1))));

        Drawn drawn = drawn(filtering().withOpened(Option.of(opened)), DIFF, false);
        List<String> lines = text(drawn);

        assertEquals(
                "┄┄┄┄┄ │ ┄ 2 filtered changes shown · Java imports ┄",
                lines.get(1).stripTrailing());
        assertTrue(lines.exists(line -> line.contains("+ import a.C;")), lines.mkString("\n"));
        assertEquals(
                List.of(true, false),
                drawn.stops()
                        .map(stop -> stop.mark().anchor())
                        .filter(Anchor.Filtered.class::isInstance)
                        .map(anchor -> ((Anchor.Filtered) anchor).opened()));
    }

    @Test
    void anOpenedMarkerStaysUnderTheMarkerOfTheLinesLeftOutBeforeIt() {
        Opened opened = new Opened(new NoDiff(), HashSet.of(new Opened.Hunk("A.java", new HunkStart(40, 40))));

        List<String> lines = text(drawn(filtering().withOpened(Option.of(opened)), DIFF, false))
                .map(String::stripTrailing);
        int gap = lines.indexOf("┄┄┄┄┄ │ 17 lines hidden ┄");

        assertEquals("┄┄┄┄┄ │ ┄ 2 filtered changes shown · Java imports ┄", lines.get(gap + 1));
        assertEquals("40 40 │   y", lines.get(gap + 2));
    }

    @Test
    void theTitleSaysHowManyHunksAreHiddenOrThatTheFiltersAreOff() {
        assertEquals(
                Option.of("2 hunks filtered"), drawn(filtering(), DIFF, false).note());
        assertEquals(
                Option.of("filters off"),
                drawn(filtering().withOff(true), DIFF, false).note());
        assertEquals(Option.none(), drawn(Filtering.NONE, DIFF, false).note());
    }

    @Test
    void switchedOffTheyHideNothing() {
        List<String> lines = text(drawn(filtering().withOff(true), DIFF, false));

        assertTrue(lines.exists(line -> line.contains("+ import a.C;")));
        assertTrue(lines.forAll(line -> !line.contains("filtered changes")));
    }

    @Test
    void aFileAllHiddenIsLeftOutOfADiffOfSeveralFiles() {
        List<String> lines = text(drawn(filtering(), IMPORTS_ONLY, false));

        assertEquals(
                List.of(" modified  b.txt", "1   │ - old", "  1 │ + new", "┄┄┄ │ more ┄"),
                lines.map(String::stripTrailing));
    }

    @Test
    void aFileAllHiddenKeepsItsBarAndSaysSoInTheWorkingTree() {
        List<String> lines = text(drawn(filtering().withKeepEmptyFiles(true), IMPORTS_ONLY, false));

        assertEquals(
                List.of(
                        " modified  A.java",
                        "All changes hidden by filters (Java imports)",
                        "",
                        " modified  b.txt",
                        "1   │ - old",
                        "  1 │ + new",
                        "┄┄┄ │ more ┄"),
                lines.map(String::stripTrailing));
    }

    @Test
    void theSplitViewDrawsTheMarkerAcrossBothSides() {
        List<String> lines = text(drawn(filtering(), DIFF, true));

        assertEquals(2, lines.count(line -> line.contains("┄ 2 filtered changes · Java imports ┄")));
        assertTrue(lines.exists(line -> line.contains("int b;")));
        assertTrue(lines.forAll(line -> !line.contains("import a.C;")));
    }

    @Test
    void withoutFiltersNothingIsHidden() {
        Expansion.Folded folded = Filtering.NONE.hide(Expansion.Folded.of(DIFF), DIFF);

        assertEquals(DIFF, folded.diff());
    }

    private static Filtering filtering() {
        return new Filtering(List.of(IMPORTS), Option.none(), List.empty(), false, false);
    }

    private static Drawn drawn(Filtering filtering, ParsedDiff diff, boolean split) {
        return RichDiff.drawn(
                filtering.hide(Expansion.Folded.of(diff), diff),
                Option.none(),
                Option.none(),
                RichDiff.Drawing.of(Theme.SpanStyles.NONE)
                        .withHighlights(HashSet.empty())
                        .withWidth(200)
                        .withSplit(split ? RichDiff.Split.SIDE_BY_SIDE : RichDiff.Split.UNIFIED));
    }

    private static List<String> text(Drawn drawn) {
        return drawn.lines()
                .map((Line line) -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }

    /**
     * Content that is no diff.
     */
    private record NoDiff() implements ScrolledContent.Other {}
}
