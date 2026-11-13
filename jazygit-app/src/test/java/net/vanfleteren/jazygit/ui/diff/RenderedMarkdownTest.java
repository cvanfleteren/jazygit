package net.vanfleteren.jazygit.ui.diff;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.Optional;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * A version of a Markdown file rendered (ADR 103).
 */
class RenderedMarkdownTest {

    private static final String README = """
            diff --git a/README.md b/README.md
            --- a/README.md
            +++ b/README.md
            @@ -1,7 +1,7 @@
             Intro text.
            \s
            -Old text.
            +New text.
            \s
             - one
             - two
            """;

    @Test
    void eachTopLevelBlockIsDrawnWithTheLinesItWasParsedFrom() {
        List<RenderedMarkdown.Block> blocks = RenderedMarkdown.blocks(
                Vector.of("Intro", "text.", "", "- one", "- two", "", "Last."), 40, Theme.SpanStyles.NONE);

        assertEquals(List.of(0, 3, 4, 6), blocks.map(RenderedMarkdown.Block::first));
        assertEquals(List.of(1, 3, 4, 6), blocks.map(RenderedMarkdown.Block::last));
        // The items of a tight list follow each other without a blank line.
        assertEquals(List.of(false, true, false, true), blocks.map(RenderedMarkdown.Block::spaced));
        assertEquals(List.of("Intro text."), text(blocks.head().rows()));
    }

    @Test
    void theItemsOfALooseListAreSetApart() {
        List<RenderedMarkdown.Block> blocks =
                RenderedMarkdown.blocks(Vector.of("- one", "", "- two"), 40, Theme.SpanStyles.NONE);

        assertEquals(List.of(false, true), blocks.map(RenderedMarkdown.Block::spaced));
    }

    @Test
    void frontMatterIsACodeBlockOfAsManyLines() {
        assertEquals(
                Vector.of("```yaml", "title: x", "```", "text"),
                RenderedMarkdown.withFrontMatter(Vector.of("---", "title: x", "---", "text")));
        assertEquals(Vector.of("text", "---"), RenderedMarkdown.withFrontMatter(Vector.of("text", "---")));
    }

    @Test
    void aCommentOfHtmlIsLeftOutButItsLinesStay() {
        assertEquals(
                Vector.of("a ", "", " b", "c"),
                RenderedMarkdown.withoutComments(Vector.of("a <!-- one", "two", "three --> b", "c")));
        assertEquals(
                Vector.of("", "", "text"),
                RenderedMarkdown.withoutComments(Vector.of("<!-- a block", "of comment -->", "text")));
    }

    @Test
    void whatLooksLikeACommentInCodeStays() {
        Vector<String> code =
                Vector.of("HTML comments (`<!-- -->`), taken out.", "", "```html", "<!-- kept -->", "```");

        assertEquals(code, RenderedMarkdown.withoutComments(code));
    }

    @Test
    void aRelativeLinkIsItsTextAndAnAbsoluteOneStays() {
        assertEquals(
                "see the *guide* and [the site](https://example.com) or [mail](mailto:a@b.c), top",
                RenderedMarkdown.withoutRelativeLinks(
                        "see [the *guide*](docs/guide.md) and [the site](https://example.com) or [mail](mailto:a@b.c),"
                                + " [top](#top)"));
    }

    @Test
    void theNewVersionShowsItsBarThenItsBlocksWithAMarkerBesideTheOneThatChanged() {
        RichDiff.Drawn drawn = drawn(FileVersion.NEW, false);

        List<String> lines = text(drawn.lines());
        assertTrue(lines.head().startsWith(" new version  README.md "), lines.head());
        assertEquals(
                List.of("  Intro text.", "", "+ New text.", "", "  • one", "  • two"),
                lines.tail().map(String::stripTrailing));
    }

    @Test
    void aThemeThatTintsChangedLinesHasABarInTheColourOfTheirMarkerNotOfTheTint() {
        Theme.SpanStyles styles = cssClass -> switch (cssClass) {
            case Theme.Classes.DIFF_ADDED_LINE -> Style.EMPTY.fg(Color.GREEN).bg(Color.BLACK);
            case Theme.Classes.DIFF_ADDED -> Style.EMPTY.fg(Color.LIGHT_GREEN);
            default -> Style.EMPTY;
        };

        Span bar = drawn(FileVersion.NEW, false, styles).lines().get(3).spans().getFirst();

        assertEquals("▎", bar.content());
        assertEquals(Optional.of(Color.LIGHT_GREEN), bar.style().fg());
    }

    @Test
    void theOriginalMarksTheBlockThatChangedAsRemoved() {
        List<String> lines = text(drawn(FileVersion.ORIGINAL, false).lines());

        assertEquals("- Old text.", lines.get(3).stripTrailing());
    }

    @Test
    void theBarIsTheOnlyStopAndEveryLineIsPlacedOnTheFirstRowOfABlock() {
        RichDiff.Drawn drawn = drawn(FileVersion.NEW, false);

        assertEquals(
                List.of(new Anchor.File("README.md")),
                drawn.stops().map(stop -> stop.mark().anchor()));
        assertEquals(
                List.of(
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 1), 1),
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 3), 3),
                        // A blank line goes with the block after it, after that block's own lines.
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 2), 3),
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 5), 5),
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 4), 5),
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 6), 6)),
                drawn.places());
    }

    @Test
    void aVersionReadUpToACutSaysSoAtItsEnd() {
        List<String> lines = text(drawn(FileVersion.NEW, true).lines());

        assertEquals("the file is too long to render the rest of", lines.last());
    }

    @Test
    void aCodeBlockIsColouredByTheLexer() {
        List<Line> rows = RenderedMarkdown.blocks(
                        Vector.of("```java", "return x;", "```"), 40, RichDiffTest.SYNTAX_STYLES)
                .head()
                .rows();

        Span keyword = List.ofAll(rows.get(1).spans())
                .find(span -> span.content().equals("return"))
                .get();
        assertEquals(
                RichDiffTest.SYNTAX_STYLES.of(Theme.Classes.SYNTAX_KEYWORD).fg(),
                keyword.style().fg());
    }

    @Test
    void eachColumnOfATableIsAsWideAsItsWidestCellHoweverWideTheTable() {
        List<RenderedMarkdown.Block> blocks = RenderedMarkdown.blocks(
                Vector.of(
                        "| Module | Holds |",
                        "|---|---|",
                        "| `jazygit-json` | json: JSON as plain values, and the decoders that turn it into records |",
                        "| `jazygit-git` | git: running git |"),
                40,
                Theme.SpanStyles.NONE);

        assertEquals(
                List.of(
                        "Module        Holds",
                        "jazygit-json  json: JSON as plain values, and the decoders that turn it into records",
                        "jazygit-git   git: running git"),
                text(blocks.head().rows()).map(String::stripTrailing));
    }

    @Test
    void theCellsOfATableAreAlignedAsItsDashesSayAndAnEscapedPipeStaysInItsCell() {
        Option<List<Line>> table = RenderedMarkdown.table(
                "| left | right | middle |\n|:--|--:|:-:|\n| a | 1 | x |\n| a \\| b | 22 | yyy |",
                LinkedHashMap.empty(),
                Theme.SpanStyles.NONE);

        assertEquals(
                List.of("left   right  middle", "a          1    x", "a | b     22   yyy"),
                text(table.get()).map(String::stripTrailing));
        assertTrue(table.get().head().spans().stream()
                .filter(span -> !span.content().isBlank())
                .allMatch(span -> span.style().addModifiers().contains(dev.tamboui.style.Modifier.BOLD)));
    }

    @Test
    void aHeadingUnderlinedWithDashesIsNoTable() {
        assertEquals(Option.none(), RenderedMarkdown.table("Title\n---", LinkedHashMap.empty(), Theme.SpanStyles.NONE));
    }

    @Test
    void aHeaderOfMoreCellsThanItsDashesIsNoTable() {
        assertEquals(
                Option.none(),
                RenderedMarkdown.table(
                        "| A | B | C |\n|---|---|\n| a | b | c |", LinkedHashMap.empty(), Theme.SpanStyles.NONE));
    }

    @Test
    void aTableRightAfterALineOfTextIsATableOfItsOwn() {
        List<RenderedMarkdown.Block> blocks = RenderedMarkdown.blocks(
                Vector.of("Options:", "| A | B |", "|---|---|", "| a | b |"), 60, Theme.SpanStyles.NONE);

        assertEquals(List.of(0, 1), blocks.map(RenderedMarkdown.Block::first));
        assertEquals(List.of("A  B", "a  b"), text(blocks.last().rows()).map(String::stripTrailing));
    }

    @Test
    void aCellHoldsTextOnlyNotABlock() {
        Option<List<Line>> table = RenderedMarkdown.table(
                "| a | b | c | d | e |\n|---|---|---|---|---|\n| - | # h | > q | --- | 1. x |",
                LinkedHashMap.empty(),
                Theme.SpanStyles.NONE);

        assertEquals(
                List.of("-", "# h", "> q", "---", "1. x"),
                List.of(text(table.get()).last().strip().split(" {2,}")));
    }

    @Test
    void aReferenceLinkInACellIsItsText() {
        List<RenderedMarkdown.Block> blocks = RenderedMarkdown.blocks(
                Vector.of("| A | B |", "|---|---|", "| [docs][d] | x |", "", "[d]: https://example.com"),
                60,
                Theme.SpanStyles.NONE);

        assertEquals("docs  x", text(blocks.head().rows()).last().stripTrailing());
    }

    @Test
    void aLabelOverTwoLinesOrWithMoreBlanksFindsItsDefinition() {
        List<RenderedMarkdown.Block> wrapped = RenderedMarkdown.blocks(
                Vector.of(
                        "See the [Keep a", "Changelog] format.", "", "[keep a changelog]: https://keepachangelog.com"),
                60,
                Theme.SpanStyles.NONE);
        List<RenderedMarkdown.Block> blanks = RenderedMarkdown.blocks(
                Vector.of("See [x][Keep  a] and [ y ].", "", "[keep a]: https://k.com", "[y]: https://y.com"),
                60,
                Theme.SpanStyles.NONE);

        assertEquals(
                "See the Keep a Changelog format.",
                String.join(" ", text(wrapped.head().rows())));
        // Resolved, so drawn without its brackets; the text of a link keeps the blanks inside them.
        assertEquals("See x and  y .", text(blanks.head().rows()).head().stripTrailing());
    }

    @Test
    void aBlockIsDrawnWithOnlyTheLinkDefinitionsItRefersTo() {
        LinkedHashMap<String, String> definitions =
                LinkedHashMap.of("a", "[a]: https://a.example", "b", "[B]: https://b.example");

        assertEquals("\n\n[B]: https://b.example", RenderedMarkdown.referred("see [the b][B]", definitions));
        assertEquals("", RenderedMarkdown.referred("no links", definitions));
    }

    @Test
    void aLanguageIsNamedAsAnExtensionOrAProgram() {
        assertTrue(LexedHighlighter.named("java").isDefined());
        assertTrue(LexedHighlighter.named("python").isDefined());
        assertEquals(Option.none(), LexedHighlighter.named("klingon"));
        assertEquals(Option.none(), LexedHighlighter.named(""));
    }

    private static RichDiff.Drawn drawn(FileVersion version, boolean cut) {
        return drawn(version, cut, Theme.SpanStyles.NONE);
    }

    private static RichDiff.Drawn drawn(FileVersion version, boolean cut, Theme.SpanStyles styles) {
        ParsedDiff.ParsedFile whole = ParsedDiff.of(README).files().head();
        return RichDiff.drawn(
                new Expansion.Folded(
                        new ParsedDiff(List.empty(), List.of(version.of(whole))),
                        HashMap.of("README.md", Expansion.Fold.of(version, cut, 1, true))),
                true,
                true,
                Option.none(),
                Option.none(),
                styles,
                40);
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
