package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.Expansion;
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
    void theOriginalMarksTheBlockThatChangedAsRemoved() {
        List<String> lines = text(drawn(FileVersion.ORIGINAL, false).lines());

        assertEquals("- Old text.", lines.get(3).stripTrailing());
    }

    @Test
    void theBarIsTheOnlyStopAndEachLineIsPlacedOnTheFirstRowOfItsBlock() {
        RichDiff.Drawn drawn = drawn(FileVersion.NEW, false);

        assertEquals(
                List.of(new Anchor.File("README.md")),
                drawn.stops().map(stop -> stop.mark().anchor()));
        assertEquals(
                List.of(
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 1), 1),
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 3), 3),
                        new RichDiff.Place(new Anchor.OnLine("README.md", Side.RIGHT, 5), 5),
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
    void aLanguageIsNamedAsAnExtensionOrAProgram() {
        assertTrue(LexedHighlighter.named("java").isDefined());
        assertTrue(LexedHighlighter.named("python").isDefined());
        assertEquals(Option.none(), LexedHighlighter.named("klingon"));
        assertEquals(Option.none(), LexedHighlighter.named(""));
    }

    private static RichDiff.Drawn drawn(FileVersion version, boolean cut) {
        ParsedDiff.ParsedFile whole = ParsedDiff.of(README).files().head();
        return RichDiff.drawn(
                new Expansion.Folded(
                        new ParsedDiff(List.empty(), List.of(version.of(whole))),
                        HashMap.of("README.md", Expansion.Fold.of(version, cut, 1, true))),
                true,
                true,
                Option.none(),
                Option.none(),
                Theme.SpanStyles.NONE,
                40);
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
