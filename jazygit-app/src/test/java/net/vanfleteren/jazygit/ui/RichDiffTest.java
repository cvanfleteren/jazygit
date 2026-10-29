package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RichDiffTest {

    @Test
    void drawsABarForEachFileAndNumbersItsLines() {
        List<String> lines = text(RichDiff.lines("""
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
                """, Theme.SpanStyles.NONE));

        assertEquals(
                List.of(
                        " a.txt | 2 +-",
                        " 1 file changed",
                        "",
                        " modified  a.txt ",
                        // Where the hunk starts, with the heading git gives it but not its numbers, which the gutter
                        // has.
                        "┄┄┄┄┄ │ section",
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
    void aHunkWithoutAHeadingStartsWithARuleAsWideAsItsLineNumbers() {
        List<String> lines = text(RichDiff.lines("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -one
                +two
                """, Theme.SpanStyles.NONE));

        // The changed lines are padded to the width of the bar.
        assertEquals(List.of(" modified  a.txt ", "┄┄┄ │ ", "1   │ -one       ", "  1 │ +two       "), lines);
    }

    @Test
    void highlightsTheChangedWordsOfAChangedLine() {
        Style word = Style.EMPTY.reversed();
        Theme.SpanStyles styles = cssClass -> cssClass.endsWith("-word") ? word : Style.EMPTY;

        List<Line> lines = RichDiff.lines("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -count = 1
                +count = 2
                """, styles);

        assertEquals(
                List.of("2"),
                List.ofAll(lines.last().spans())
                        .filter(span -> span.style().equals(word))
                        .map(Span::content));
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
