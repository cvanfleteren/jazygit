package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.syntax.Lexer;
import net.vanfleteren.jazygit.syntax.Token;
import org.junit.jupiter.api.Test;

class RichDiffTest {

    @Test
    void drawsABarForEachFileAndNumbersItsLines() {
        List<String> lines = text(RichDiff.lines(ParsedDiff.of("""
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
                        // The first hunk has no rule: the bar is right above it.
                        " 9  9 │  nine",
                        // Changed lines are padded to the width of the file, so their background makes a block.
                        "10    │ -ten = 1 ",
                        "   10 │ +ten = 2 ",
                        "",
                        " renamed  old.txt → new.txt ",
                        "renamed, contents unchanged"),
                lines);
    }

    @Test
    void aHunkHeaderWhoseNumbersCannotBeReadFollowsTheRuleAsGitPrintedIt() {
        List<String> lines = text(RichDiff.lines(ParsedDiff.of("""
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
        List<String> lines = text(RichDiff.lines(ParsedDiff.of("""
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
                        " conflicted  f.txt   ",
                        "┄┄┄ │ @@@ -1,2 -1,2 +1,3 @@@",
                        "    │   a",
                        "    │ ++<<<<<<< HEAD",
                        "    │  +B2"),
                lines);
    }

    @Test
    void aHunkAfterTheFirstStartsWithARuleAsWideAsItsLineNumbers() {
        List<String> lines = text(RichDiff.lines(ParsedDiff.of("""
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
                List.of(" modified  a.txt ", "1   │ -one       ", "  1 │ +two       ", "┄┄┄ │ ", "5   │ -five      "),
                lines);
    }

    @Test
    void highlightsTheChangedWordsOfAChangedLine() {
        Style word = Style.EMPTY.reversed();
        Theme.SpanStyles styles = cssClass -> cssClass.endsWith("-word") ? word : Style.EMPTY;

        List<Line> lines = RichDiff.lines(ParsedDiff.of("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -count = 1
                +count = 2
                """), true, styles);

        assertEquals(
                List.of("2"),
                List.ofAll(lines.last().spans())
                        .filter(span -> span.style().equals(word))
                        .map(Span::content));
    }

    @Test
    void coloursTheCodeOfAChangedLineByItsSyntaxOnItsBackground() {
        List<Line> lines = RichDiff.lines(ParsedDiff.of(STRING_CHANGED), true, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("String s = ", Style.EMPTY.bg(Color.BLACK)),
                        Tuple.of("\"", Style.EMPTY.fg(Color.MAGENTA).bg(Color.BLACK)),
                        Tuple.of("ac", Style.EMPTY.fg(Color.MAGENTA).bg(Color.WHITE)),
                        Tuple.of("\"", Style.EMPTY.fg(Color.MAGENTA).bg(Color.BLACK)),
                        Tuple.of(";", Style.EMPTY.bg(Color.BLACK))),
                code(lines.last()));
    }

    @Test
    void theMarkerOfAChangedLineKeepsTheStyleOfItsClass() {
        List<Line> lines = RichDiff.lines(ParsedDiff.of(STRING_CHANGED), true, SYNTAX_STYLES);

        assertEquals(
                Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK),
                lines.last().spans().get(1).style());
    }

    @Test
    void aContextLineTakesTheColoursOfItsTokensOnly() {
        List<Line> lines = RichDiff.lines(ParsedDiff.of("""
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
                        Tuple.of(" ", Style.EMPTY),
                        Tuple.of("return", Style.EMPTY.fg(Color.BLUE)),
                        Tuple.of(" x;", Style.EMPTY)),
                List.ofAll(lines.get(1).spans()).drop(1).map(span -> Tuple.of(span.content(), span.style())));
    }

    @Test
    void withoutSyntaxAChangedLineIsDrawnInItsClassesAlone() {
        List<Line> lines = RichDiff.lines(ParsedDiff.of(STRING_CHANGED), false, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("String s = \"", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK)),
                        Tuple.of("ac", Style.EMPTY.fg(Color.GREEN).bg(Color.WHITE)),
                        Tuple.of("\";", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK))),
                code(lines.last()));
    }

    @Test
    void aFileOfNoLanguageKnownIsDrawnInItsClassesAlone() {
        List<Line> lines =
                RichDiff.lines(ParsedDiff.of(STRING_CHANGED.replace("A.java", "a.txt")), true, SYNTAX_STYLES);

        assertEquals(
                List.of(
                        Tuple.of("String s = \"", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK)),
                        Tuple.of("ac", Style.EMPTY.fg(Color.GREEN).bg(Color.WHITE)),
                        Tuple.of("\";", Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK))),
                code(lines.last()));
    }

    @Test
    void aChangedLineWithoutABackgroundIsDrawnInItsClassesAlone() {
        Style word = Style.EMPTY.fg(Color.GREEN).reversed();
        Theme.SpanStyles noBackground = cssClass -> cssClass.endsWith("-word")
                ? word
                : cssClass.startsWith("diff-") ? Style.EMPTY.fg(Color.GREEN) : SYNTAX_STYLES.of(cssClass);

        List<Line> lines = RichDiff.lines(ParsedDiff.of(STRING_CHANGED), true, noBackground);

        assertEquals(
                List.of(
                        Tuple.of("String s = \"", Style.EMPTY.fg(Color.GREEN)),
                        Tuple.of("ac", word),
                        Tuple.of("\";", Style.EMPTY.fg(Color.GREEN))),
                code(lines.last()));
    }

    @Test
    void outsideATokenAChangedLineKeepsTheAttributesOfItsClass() {
        Style line = Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK).bold();
        Theme.SpanStyles bold =
                cssClass -> cssClass.equals(Theme.Classes.DIFF_ADDED_LINE) ? line : SYNTAX_STYLES.of(cssClass);

        List<Line> lines = RichDiff.lines(ParsedDiff.of(STRING_CHANGED), true, bold);

        assertEquals(
                Tuple.of("String s = ", Style.EMPTY.bg(Color.BLACK).bold()),
                code(lines.last()).head());
        assertEquals(
                Tuple.of("\"", Style.EMPTY.fg(Color.MAGENTA).bg(Color.BLACK).bold()),
                code(lines.last()).get(1));
    }

    @Test
    void aLexedLineWithoutTokensTakesThePlainColourOnItsBackground() {
        List<Line> lines = RichDiff.lines(ParsedDiff.of("""
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
                code(lines.last()));
    }

    @Test
    void aLineTooLongToBeLexedIsDrawnInItsClassesAlone() {
        String tooLong = "int x = " + "1".repeat(Lexer.MAX_LINE) + ";";
        List<Line> lines = RichDiff.lines(ParsedDiff.of("""
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
                code(lines.last()).head());
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

    private static final String STRING_CHANGED = """
            diff --git a/A.java b/A.java
            --- a/A.java
            +++ b/A.java
            @@ -1 +1 @@
            -String s = "ab";
            +String s = "ac";
            """;

    private static final Theme.SpanStyles SYNTAX_STYLES = cssClass -> switch (cssClass) {
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

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
