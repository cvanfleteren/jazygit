package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.syntax.Lexer;
import net.vanfleteren.jazygit.syntax.Token;
import org.junit.jupiter.api.Test;

class RichDiffTest {

    @Test
    void drawsABarForEachFileAndNumbersItsLines() {
        List<String> lines = text(lines(ParsedDiff.of("""
                 a.txt | 2 +-
                 1 file changed

                diff --git a/a.txt b/a.txt
                index 5626abf..814f4a4 100644
                --- a/a.txt
                +++ b/a.txt
                @@ -9,2 +9,2 @@ section
                 nine
                -ten = 1
                +ten = 2
                diff --git a/old.txt b/new.txt
                similarity index 100%
                rename from old.txt
                rename to new.txt
                """), true, Theme.SpanStyles.NONE));

        assertEquals(
                List.of(
                        " a.txt | 2 +-",
                        " 1 file changed",
                        "",
                        " modified  a.txt ",
                        // The first hunk has no rule, but a marker of the lines above it (ADR 77).
                        "┄┄┄┄┄ │ 8 lines hidden ┄",
                        " 9  9 │   nine",
                        // Changed lines are padded to the width of the file, so their background makes a block.
                        "10    │ - ten = 1",
                        "   10 │ + ten = 2",
                        // How many lines follow is not known until the file is read whole.
                        "┄┄┄┄┄ │ more ┄",
                        "",
                        " renamed  old.txt → new.txt ",
                        "renamed, contents unchanged"),
                lines);
    }

    @Test
    void theMarkersSayHowManyLinesTheyLeaveOutOnceTheFileIsReadWholeAndAreStops() {
        ParsedDiff diff = ParsedDiff.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -9,2 +9,2 @@
                 nine
                -ten
                +TEN
                """);
        Expansion.Folded known = new Expansion.Folded(
                diff, HashMap.of("a.txt", new Expansion.Fold(Option.of(12), Expansion.Read.WHOLE)));
        Expansion.Folded reading = new Expansion.Folded(
                diff, HashMap.of("a.txt", new Expansion.Fold(Option.none(), Expansion.Read.READING)));

        RichDiff.Drawn drawn = RichDiff.drawn(known, true, true, Option.none(), Option.none(), Theme.SpanStyles.NONE);

        assertEquals(
                List.of(
                        " modified  a.txt",
                        "┄┄┄┄┄ │ 8 lines hidden ┄",
                        " 9  9 │   nine",
                        "10    │ - ten",
                        "   10 │ + TEN",
                        "┄┄┄┄┄ │ 2 lines hidden ┄"),
                text(drawn.lines()).map(String::stripTrailing));
        assertEquals(
                List.of(new Anchor.Gap("a.txt", 1, Option.of(9)), new Anchor.Gap("a.txt", 11, Option.of(13))),
                drawn.stops().map(stop -> stop.mark().anchor()).filter(Anchor.Gap.class::isInstance));
        assertEquals(
                List.of("┄┄┄┄┄ │ reading the whole file ┄", "┄┄┄┄┄ │ reading the whole file ┄"),
                text(RichDiff.drawn(reading, true, true, Option.none(), Option.none(), Theme.SpanStyles.NONE)
                                .lines())
                        .filter(line -> line.startsWith("┄")));
    }

    @Test
    void aFileReadOnlyUpToTheCutSaysItIsTooLongOncePastTheCutAndEverywhereWhenItCannotBeFolded() {
        ParsedDiff diff = ParsedDiff.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -9,2 +9,2 @@
                 nine
                -ten
                +TEN
                """);
        // Read up to line 30: the lines after the last hunk can be shown up to there, and not past it.
        Expansion.Folded beforeTheCut = new Expansion.Folded(
                diff, HashMap.of("a.txt", new Expansion.Fold(Option.none(), Expansion.Read.CUT, Option.of(30))));
        Expansion.Folded atTheCut = new Expansion.Folded(
                diff, HashMap.of("a.txt", new Expansion.Fold(Option.none(), Expansion.Read.CUT, Option.of(10))));
        Expansion.Folded tooLong = new Expansion.Folded(
                diff, HashMap.of("a.txt", new Expansion.Fold(Option.none(), Expansion.Read.TOO_LONG)));

        assertEquals(List.of("┄┄┄┄┄ │ 8 lines hidden ┄", "┄┄┄┄┄ │ more ┄"), markers(beforeTheCut));
        assertEquals(
                List.of("┄┄┄┄┄ │ 8 lines hidden ┄", "┄┄┄┄┄ │ the file is too long to show more of ┄"),
                markers(atTheCut));
        assertEquals(
                List.of(
                        "┄┄┄┄┄ │ the file is too long to show more of ┄",
                        "┄┄┄┄┄ │ the file is too long to show more of ┄"),
                markers(tooLong));
    }

    @Test
    void aVersionIsDrawnUnderItsOwnBarWithItsNumbersInItsColumnOfTheDiffsAndNoMarkers() {
        String renamed = """
                diff --git a/old.txt b/new.txt
                similarity index 50%
                rename from old.txt
                rename to new.txt
                --- a/old.txt
                +++ b/new.txt
                @@ -1,3 +1,3 @@
                 one
                -two
                +TWO
                 three
                """;
        ParsedDiff.ParsedFile whole = ParsedDiff.of(renamed).files().head();

        RichDiff.Drawn added = drawn(whole, FileVersion.NEW, false);
        RichDiff.Drawn original = drawn(whole, FileVersion.ORIGINAL, true);

        assertEquals(
                List.of(" new version  new.txt", "  1 │   one", "  2 │ + TWO", "  3 │   three"),
                text(added.lines()).map(String::stripTrailing));
        // The gutter is as wide as the diff's, so switching does not move the code sideways.
        assertEquals(
                text(lines(ParsedDiff.of(renamed), false, Theme.SpanStyles.NONE))
                        .get(1)
                        .indexOf("│"),
                text(added.lines()).get(1).indexOf("│"));
        assertEquals(
                List.of(
                        " original  old.txt",
                        "1   │   one",
                        "2   │ - two",
                        "3   │   three",
                        "the file is too long to show the rest of"),
                text(original.lines()).map(String::stripTrailing));
        // Its lines are stops, but in no hunk: a range is of the diff.
        assertTrue(added.stops()
                .filter(stop -> stop.mark().anchor() instanceof Anchor.OnLine)
                .forAll(stop -> stop.mark().hunk().isEmpty()));
        assertEquals(4, added.stops().size());
    }

    @Test
    void aVersionOfMoreLinesThanItsDiffNumbersKeepsTheGutterOfTheDiff() {
        // The diff ends at line 95, two digits; the file has 120 lines, three.
        ParsedDiff.ParsedFile whole = ParsedDiff.of("diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n"
                        + "@@ -1,120 +1,120 @@\n"
                        + List.rangeClosed(1, 120)
                                .map(n -> n == 93 ? "-old\n+new\n" : " line" + n + "\n")
                                .mkString())
                .files()
                .head();
        // Two columns of two digits and a space between them, then a space: as in the diff.
        int bar = "93 93 │".indexOf('│');

        RichDiff.Drawn added = drawn(whole, FileVersion.NEW, false, 2, Theme.SpanStyles.NONE);
        RichDiff.Drawn original = drawn(whole, FileVersion.ORIGINAL, false, 2, Theme.SpanStyles.NONE);

        assertTrue(
                text(added.lines()).tail().forAll(line -> line.indexOf('│') == bar),
                text(added.lines()).mkString("\n"));
        assertTrue(text(original.lines()).tail().forAll(line -> line.indexOf('│') == bar));
        assertEquals("  120 │   line120", text(added.lines()).last().stripTrailing());
        assertEquals("120   │   line120", text(original.lines()).last().stripTrailing());
        assertEquals("   93 │ + new", text(added.lines()).get(93).stripTrailing());
    }

    @Test
    void aVersionLeavesTheMarkerOfAChangedLineBlankWhenTheThemeTintsTheLine() {
        ParsedDiff.ParsedFile whole = ParsedDiff.of("""
                        diff --git a/a.md b/a.md
                        --- a/a.md
                        +++ b/a.md
                        @@ -1,2 +1,2 @@
                         # Title
                        -- old item
                        +- new item
                        """).files().head();

        List<String> tinted = text(drawn(whole, FileVersion.NEW, false, SYNTAX_STYLES)
                        .lines())
                .map(String::stripTrailing);
        List<String> untinted = text(drawn(whole, FileVersion.NEW, false, Theme.SpanStyles.NONE)
                        .lines())
                .map(String::stripTrailing);

        assertEquals(List.of(" new version  a.md", "  1 │   # Title", "  2 │   - new item"), tinted);
        // Without a background, the marker is all that tells the line changed.
        assertEquals("  2 │ + - new item", untinted.last());
    }

    private static RichDiff.Drawn drawn(ParsedDiff.ParsedFile whole, FileVersion version, boolean cut) {
        return drawn(whole, version, cut, Theme.SpanStyles.NONE);
    }

    private static RichDiff.Drawn drawn(
            ParsedDiff.ParsedFile whole, FileVersion version, boolean cut, Theme.SpanStyles styles) {
        return drawn(whole, version, cut, 1, styles);
    }

    private static RichDiff.Drawn drawn(
            ParsedDiff.ParsedFile whole, FileVersion version, boolean cut, int digits, Theme.SpanStyles styles) {
        return RichDiff.drawn(
                new Expansion.Folded(
                        new ParsedDiff(List.empty(), List.of(version.of(whole))),
                        HashMap.of(whole.file().path(), Expansion.Fold.of(version, cut, digits))),
                true,
                true,
                Option.none(),
                Option.none(),
                styles);
    }

    /**
     * The markers {@code folded} is drawn with.
     */
    private static List<String> markers(Expansion.Folded folded) {
        return text(RichDiff.drawn(folded, true, true, Option.none(), Option.none(), Theme.SpanStyles.NONE)
                        .lines())
                .filter(line -> line.startsWith("┄"));
    }

    private static final String VERSIONS = """
            diff --git a/pom.xml b/pom.xml
            --- a/pom.xml
            +++ b/pom.xml
            @@ -43,4 +43,4 @@
             <spotless>3.10.4</spotless>
            -<checkstyle>14.3.0</checkstyle>
            -<errorprone>2.51.0</errorprone>
            -<native>1.1.17</native>
            +<checkstyle>14.4.0</checkstyle>
            +<errorprone>2.52.0</errorprone>
            +<native>1.1.18</native>
            """;

    @Test
    void changedLinesAreShownInPairsAndTheCursorStopsOnThemInThatOrder() {
        RichDiff.Drawn drawn =
                RichDiff.drawn(ParsedDiff.of(VERSIONS), true, true, Option.none(), Theme.SpanStyles.NONE);

        assertEquals(
                List.of(
                        " modified  pom.xml",
                        "┄┄┄┄┄ │ 42 lines hidden ┄",
                        "43 43 │   <spotless>3.10.4</spotless>",
                        // Each removed line is followed by the line it became (ADR 83).
                        "44    │ - <checkstyle>14.3.0</checkstyle>",
                        "   44 │ + <checkstyle>14.4.0</checkstyle>",
                        "45    │ - <errorprone>2.51.0</errorprone>",
                        "   45 │ + <errorprone>2.52.0</errorprone>",
                        "46    │ - <native>1.1.17</native>",
                        "   46 │ + <native>1.1.18</native>",
                        "┄┄┄┄┄ │ more ┄"),
                text(drawn.lines()).map(String::stripTrailing));
        assertEquals(
                List.of(
                        new Anchor.OnLine("pom.xml", Side.LEFT, 44),
                        new Anchor.OnLine("pom.xml", Side.RIGHT, 44),
                        new Anchor.OnLine("pom.xml", Side.LEFT, 45),
                        new Anchor.OnLine("pom.xml", Side.RIGHT, 45),
                        new Anchor.OnLine("pom.xml", Side.LEFT, 46),
                        new Anchor.OnLine("pom.xml", Side.RIGHT, 46)),
                drawn.stops()
                        .map(stop -> stop.mark().anchor())
                        .filter(anchor ->
                                anchor instanceof Anchor.OnLine(var path, var side, int number) && number > 43));
    }

    @Test
    void theHunkARangeIsOpenInIsShownAsGitGivesIt() {
        List<String> lines = text(RichDiff.drawn(
                                Expansion.Folded.of(ParsedDiff.of(VERSIONS)),
                                true,
                                true,
                                Option.none(),
                                Option.of(new DiffStops.HunkAt(0, 0)),
                                Theme.SpanStyles.NONE)
                        .lines())
                .filter(line -> line.contains("│ -") || line.contains("│ +"))
                .map(String::stripTrailing);

        assertEquals(
                List.of(
                        "44    │ - <checkstyle>14.3.0</checkstyle>",
                        "45    │ - <errorprone>2.51.0</errorprone>",
                        "46    │ - <native>1.1.17</native>",
                        "   44 │ + <checkstyle>14.4.0</checkstyle>",
                        "   45 │ + <errorprone>2.52.0</errorprone>",
                        "   46 │ + <native>1.1.18</native>"),
                lines);
    }

    @Test
    void aHunkHeaderWhoseNumbersCannotBeReadFollowsTheRuleAsGitPrintedIt() {
        List<String> lines = text(lines(ParsedDiff.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ garbled
                -one
                """), true, Theme.SpanStyles.NONE));

        assertEquals(List.of(" modified  a.txt ", "┄┄┄ │ @@ garbled", "    │ -one"), lines);
    }

    @Test
    void aConflictedFileShowsUnderABarOfItsOwnAsGitPrintedIt() {
        List<String> lines = text(lines(ParsedDiff.of("""
                * Unmerged path g.txt
                diff --cc f.txt
                index 189acaa,1ed2302..0000000
                --- a/f.txt
                +++ b/f.txt
                @@@ -1,2 -1,2 +1,3 @@@
                  a
                ++<<<<<<< HEAD
                 +B2
                """), true, Theme.SpanStyles.NONE));

        assertEquals(
                List.of(
                        " conflicted  g.txt ",
                        "",
                        // The bar is as wide as the widest line of the file.
                        " conflicted  f.txt    ",
                        "┄┄┄ │ @@@ -1,2 -1,2 +1,3 @@@",
                        "    │   a",
                        "    │ ++<<<<<<< HEAD",
                        "    │  +B2"),
                lines);
    }

    @Test
    void aHunkAfterTheFirstStartsWithAMarkerAsWideAsItsLineNumbers() {
        List<String> lines = text(lines(ParsedDiff.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -one
                +two
                @@ -5 +5 @@
                -five
                """), true, Theme.SpanStyles.NONE));

        // The changed lines are padded to the width of the bar.
        assertEquals(
                List.of(
                        " modified  a.txt ",
                        "1   │ - one      ",
                        "  1 │ + two      ",
                        "┄┄┄ │ 3 lines hidden ┄",
                        "5   │ - five     ",
                        "┄┄┄ │ more ┄"),
                lines);
    }

    @Test
    void highlightsTheChangedWordsOfAChangedLine() {
        Style word = Style.EMPTY.reversed();
        Theme.SpanStyles styles = cssClass -> cssClass.endsWith("-word") ? word : Style.EMPTY;

        List<Line> lines = lines(ParsedDiff.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -count = 1
                +count = 2
                """), true, styles);

        assertEquals(
                List.of("2"),
                List.ofAll(lastOfDiff(lines).spans())
                        .filter(span -> span.style().equals(word))
                        .map(Span::content));
    }

    @Test
    void coloursTheCodeOfAChangedLineByItsSyntaxOnItsBackground() {
        List<Line> lines = lines(ParsedDiff.of(STRING_CHANGED), true, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("String s = ", Style.EMPTY.bg(Color.BLACK)),
                        Tuple.of("\"", Style.EMPTY.fg(Color.MAGENTA).bg(Color.BLACK)),
                        Tuple.of("ac", Style.EMPTY.fg(Color.MAGENTA).bg(Color.WHITE)),
                        Tuple.of("\"", Style.EMPTY.fg(Color.MAGENTA).bg(Color.BLACK)),
                        Tuple.of(";", Style.EMPTY.bg(Color.BLACK))),
                code(lastOfDiff(lines)));
    }

    @Test
    void theMarkerOfAChangedLineKeepsTheStyleOfItsClass() {
        List<Line> lines = lines(ParsedDiff.of(STRING_CHANGED), true, SYNTAX_STYLES);

        assertEquals(
                Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK),
                lastOfDiff(lines).spans().get(1).style());
    }

    @Test
    void aContextLineTakesTheColoursOfItsTokensOnly() {
        List<Line> lines = lines(ParsedDiff.of("""
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -1,2 +1,2 @@
                 return x;
                -a();
                +b();
                """), true, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("  ", Style.EMPTY),
                        Tuple.of("return", Style.EMPTY.fg(Color.BLUE)),
                        Tuple.of(" x;", Style.EMPTY)),
                List.ofAll(lines.get(1).spans()).drop(1).map(span -> Tuple.of(span.content(), span.style())));
    }

    @Test
    void withoutSyntaxAChangedLineIsDrawnInItsClassesAlone() {
        List<Line> lines = lines(ParsedDiff.of(STRING_CHANGED), false, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("String s = \"", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK)),
                        Tuple.of("ac", Style.EMPTY.fg(Color.GREEN).bg(Color.WHITE)),
                        Tuple.of("\";", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK))),
                code(lastOfDiff(lines)));
    }

    @Test
    void withoutWordsAChangedLineIsDrawnInItsLineClassAlone() {
        List<Line> lines = RichDiff.drawn(ParsedDiff.of(STRING_CHANGED), false, false, Option.none(), SYNTAX_STYLES)
                .lines();

        assertEquals(
                List.of(
                        Tuple.of("String s = \"", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK)),
                        Tuple.of("ac", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK)),
                        Tuple.of("\";", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK))),
                code(lastOfDiff(lines)));
    }

    @Test
    void aFileOfNoLanguageKnownIsDrawnInItsClassesAlone() {
        List<Line> lines = lines(ParsedDiff.of(STRING_CHANGED.replace("A.java", "a.txt")), true, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("String s = \"", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK)),
                        Tuple.of("ac", Style.EMPTY.fg(Color.GREEN).bg(Color.WHITE)),
                        Tuple.of("\";", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK))),
                code(lastOfDiff(lines)));
    }

    @Test
    void aChangedLineWithoutABackgroundIsDrawnInItsClassesAlone() {
        Style word = Style.EMPTY.fg(Color.GREEN).reversed();
        Theme.SpanStyles noBackground = cssClass -> cssClass.endsWith("-word")
                ? word
                : cssClass.startsWith("diff-") ? Style.EMPTY.fg(Color.GREEN) : SYNTAX_STYLES.of(cssClass);

        List<Line> lines = lines(ParsedDiff.of(STRING_CHANGED), true, noBackground);

        assertEquals(
                List.of(
                        Tuple.of("String s = \"", Style.EMPTY.fg(Color.GREEN)),
                        Tuple.of("ac", word),
                        Tuple.of("\";", Style.EMPTY.fg(Color.GREEN))),
                code(lastOfDiff(lines)));
    }

    @Test
    void outsideATokenAChangedLineKeepsTheAttributesOfItsClass() {
        Style line = Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK).bold();
        Theme.SpanStyles bold =
                cssClass -> cssClass.equals(Theme.Classes.DIFF_ADDED_LINE) ? line : SYNTAX_STYLES.of(cssClass);

        List<Line> lines = lines(ParsedDiff.of(STRING_CHANGED), true, bold);

        assertEquals(
                Tuple.of("String s = ", Style.EMPTY.bg(Color.BLACK).bold()),
                code(lastOfDiff(lines)).head());
        assertEquals(
                Tuple.of("\"", Style.EMPTY.fg(Color.MAGENTA).bg(Color.BLACK).bold()),
                code(lastOfDiff(lines)).get(1));
    }

    @Test
    void aLexedLineWithoutTokensTakesThePlainColourOnItsBackground() {
        List<Line> lines = lines(ParsedDiff.of("""
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -1 +1 @@
                -x = foo;
                +x = bar;
                """), true, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("x = ", Style.EMPTY.bg(Color.BLACK)),
                        Tuple.of("bar", Style.EMPTY.bg(Color.WHITE)),
                        Tuple.of(";", Style.EMPTY.bg(Color.BLACK))),
                code(lastOfDiff(lines)));
    }

    @Test
    void aLineTooLongToBeLexedIsDrawnInItsClassesAlone() {
        String tooLong = "int x = " + "1".repeat(Lexer.MAX_LINE) + ";";
        List<Line> lines = lines(ParsedDiff.of("""
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -0,0 +1,2 @@
                +%s
                +int y;
                """.formatted(tooLong)), true, SYNTAX_STYLES);

        assertEquals(List.of(Tuple.of(tooLong, Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK))), code(lines.get(1)));
        assertEquals(
                Tuple.of("int", Style.EMPTY.fg(Color.BLUE).bg(Color.BLACK)),
                code(lastOfDiff(lines)).head());
    }

    @Test
    void aLineOfManyTokensIsCutQuickly() {
        int count = 32_000;
        List<Token> tokens = List.range(0, count).map(i -> new Token(2 * i, 1, Token.Kind.NUMBER));

        List<RichDiff.Piece> pieces = assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> RichDiff.pieces(List.of(new Segment("1,".repeat(count), false)), tokens));

        assertEquals(2 * count, pieces.size());
        assertEquals(new RichDiff.Piece(",", false, Option.none()), pieces.get(1));
    }

    @Test
    void theFilesAndTheLinesOfALongDiffAreDrawnInTheirOrder() {
        int files = 40;
        int lines = 300;
        String diff = List.range(0, files)
                .flatMap(file -> List.of(
                                "diff --git a/f%d.txt b/f%d.txt".formatted(file, file),
                                "--- a/f%d.txt".formatted(file),
                                "+++ b/f%d.txt".formatted(file),
                                "@@ -1,%d +1,%d @@".formatted(lines, lines))
                        .appendAll(List.rangeClosed(1, lines).map(line -> " f%d line %d".formatted(file, line))))
                .mkString("", "\n", "\n");

        List<String> drawn =
                text(lines(ParsedDiff.of(diff), true, Theme.SpanStyles.NONE)).map(String::stripTrailing);

        assertEquals(
                List.range(0, files)
                        .flatMap(file -> List.of(" modified  f%d.txt".formatted(file))
                                .appendAll(List.rangeClosed(1, lines)
                                        .map(line -> "%3d %3d │   f%d line %d".formatted(line, line, file, line)))
                                .append("┄┄┄┄┄┄┄ │ more ┄")
                                .prependAll(file > 0 ? List.of("") : List.empty())),
                drawn);
    }

    @Test
    void aChangedLineIsPaddedToTheBarWhenAWordIsCutFromItsVariationSelector() {
        // The emoji and its U+FE0F are cut into pieces of their own, which are narrower together than the whole.
        List<Line> lines = lines(ParsedDiff.of("""
                diff --git a/f.txt b/f.txt
                --- a/f.txt
                +++ b/f.txt
                @@ -1 +1 @@
                -a ❤️
                +a ✔️
                """), true, Theme.SpanStyles.NONE);

        // The marker after the last hunk is as wide as it says.
        assertEquals(List.of(17, 17, 17, 12), lines.map(RichDiffTest::width));
    }

    @Test
    void theWidestLineIsMeasuredWithoutDrawingTheLinesOfTheDiff() {
        AtomicInteger gutters = new AtomicInteger();
        Theme.SpanStyles counting = cssClass -> {
            if (cssClass.equals(Theme.Classes.DIFF_LINE_NUMBER)) {
                gutters.incrementAndGet();
            }
            return SYNTAX_STYLES.of(cssClass);
        };
        RichDiff.Drawn drawn = RichDiff.drawn(ParsedDiff.of("""
                         a.txt | 2 +-
                         a summary longer than any line of the diff, which is measured as it is drawn

                        diff --git a/A.java b/A.java
                        --- a/A.java
                        +++ b/A.java
                        @@ -1,3 +1,3 @@
                         class A {
                        -    String s = "ab"; // a line longer than the others
                        +    String s = "ac"; // a line longer than the others, and longer still
                        \\ No newline at end of file
                         }
                        diff --git a/f.txt b/f.txt
                        --- a/f.txt
                        +++ b/f.txt
                        @@ -1 +1 @@
                        -a ❤️
                        +a ✔️
                        """), true, true, Option.none(), counting);

        // The markers of the lines left out are drawn already, and resolve the class of the gutter too.
        int markers = gutters.get();
        int widest = drawn.widest().getAsInt();

        assertEquals(markers, gutters.get());
        assertEquals(drawn.lines().map(RichDiffTest::width).max().get(), widest);
        assertEquals(widest, drawn.after(List.of(Line.from("short"))).widest().getAsInt());
        assertEquals(
                200, drawn.after(List.of(Line.from("x".repeat(200)))).widest().getAsInt());
    }

    @Test
    void theWidestLineOfADiffIsItsBarWhenNoOtherLineIsWider() {
        RichDiff.Drawn drawn = RichDiff.drawn(ParsedDiff.of("""
                        diff --git a/A.java b/A.java
                        --- a/A.java
                        +++ b/A.java
                        @@ -1,2 +1,2 @@
                         class A {
                        -    String s = "ab"; // a line longer than the others
                        +    String s = "ac"; // a line longer than the others, and longer still
                        """), true, true, Option.none(), SYNTAX_STYLES);

        assertEquals(
                drawn.lines().map(RichDiffTest::width).max().get(),
                drawn.widest().getAsInt());
    }

    private static final String STRING_CHANGED = """
            diff --git a/A.java b/A.java
            --- a/A.java
            +++ b/A.java
            @@ -1 +1 @@
            -String s = "ab";
            +String s = "ac";
            """;

    static final Theme.SpanStyles SYNTAX_STYLES = cssClass -> switch (cssClass) {
        case Theme.Classes.DIFF_ADDED_LINE, Theme.Classes.DIFF_REMOVED_LINE ->
            Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK);
        case Theme.Classes.DIFF_ADDED_WORD, Theme.Classes.DIFF_REMOVED_WORD ->
            Style.EMPTY.fg(Color.GREEN).bg(Color.WHITE);
        case Theme.Classes.SYNTAX_KEYWORD -> Style.EMPTY.fg(Color.BLUE);
        case Theme.Classes.SYNTAX_STRING -> Style.EMPTY.fg(Color.MAGENTA);
        default -> Style.EMPTY;
    };

    /**
     * The code of a changed line, each span with its style: what comes after its gutter and its marker, without the
     * padding after it.
     */
    private static List<Tuple2<String, Style>> code(Line line) {
        return List.ofAll(line.spans()).drop(2).dropRight(1).map(span -> Tuple.of(span.content(), span.style()));
    }

    /**
     * How many columns {@code line} takes, as each of its spans is drawn by itself.
     */
    private static int width(Line line) {
        return line.spans().stream().mapToInt(Span::width).sum();
    }

    /**
     * The lines of {@code diff}, the changed words highlighted, as the tests of how a line is drawn expect.
     */
    private static List<Line> lines(ParsedDiff diff, boolean syntax, Theme.SpanStyles styles) {
        return RichDiff.drawn(diff, syntax, true, Option.none(), styles).lines();
    }

    /**
     * The last line of {@code lines} that is not a marker of lines left out.
     */
    private static Line lastOfDiff(List<Line> lines) {
        return lines.findLast(line -> !text(List.of(line)).head().startsWith("┄"))
                .get();
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
