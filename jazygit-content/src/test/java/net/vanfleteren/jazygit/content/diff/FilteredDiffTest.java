package net.vanfleteren.jazygit.content.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.git.model.DiffFilter;
import net.vanfleteren.jazygit.git.model.DiffFiltering.HunkStart;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Filtering;
import net.vanfleteren.jazygit.state.diff.Opened;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.ui.theme.Theme;
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
        RichDiff.Drawn drawn = drawn(filtering(), DIFF, false);

        assertEquals(
                List.of(List.of(new HunkStart(1, 1)), List.of(new HunkStart(40, 40))),
                drawn.stops()
                        .map(stop -> stop.mark().anchor())
                        .filter(Anchor.Filtered.class::isInstance)
                        .map(anchor -> ((Anchor.Filtered) anchor).starts()));
    }

    @Test
    void aHunkOpenedShows() {
        Opened opened =
                new Opened(new ScrolledContent.Note("x"), HashSet.of(new Opened.Hunk("A.java", new HunkStart(1, 1))));

        List<String> lines = text(drawn(filtering().withOpened(Option.of(opened)), DIFF, false));

        assertTrue(lines.exists(line -> line.contains("+ import a.C;")), lines.mkString("\n"));
        assertEquals(1, lines.count(line -> line.contains("filtered changes")));
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
        return new Filtering(List.of(IMPORTS), Option.none(), List.empty(), false);
    }

    private static RichDiff.Drawn drawn(Filtering filtering, ParsedDiff diff, boolean split) {
        return RichDiff.drawn(
                filtering.hide(Expansion.Folded.of(diff), diff),
                false,
                false,
                Option.none(),
                Option.none(),
                Theme.SpanStyles.NONE,
                200,
                split);
    }

    private static List<String> text(RichDiff.Drawn drawn) {
        return drawn.lines()
                .map((Line line) -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
