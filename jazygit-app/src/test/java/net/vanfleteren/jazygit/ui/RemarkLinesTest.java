package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.StylePropertyResolver;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.ui.ContentView.ShownDiff;
import net.vanfleteren.jazygit.ui.ContentView.ShownRemarks;
import org.junit.jupiter.api.Test;

/**
 * The review threads of a pull request drawn in its diff (ADR 55).
 */
class RemarkLinesTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final String DIFF = """
            diff --git a/a.txt b/a.txt
            index 1111111..2222222 100644
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
            """;

    private static ReviewThread thread(int line, boolean resolved, String... bodies) {
        return thread("a.txt", line, resolved, bodies);
    }

    private static ReviewThread thread(String path, int line, boolean resolved, String... bodies) {
        return new ReviewThread(
                path,
                Side.RIGHT,
                Option.of(line),
                Option.none(),
                Side.RIGHT,
                Option.of(line),
                resolved,
                false,
                Subject.LINE,
                List.of(bodies).map(body -> new Comment("bob", NOW.minusSeconds(7200), body, Option.none())),
                0);
    }

    private static ShownRemarks shown(boolean opened, ReviewThread... threads) {
        return new ShownRemarks(List.of(threads), opened, NOW, Option.none(), true);
    }

    private static List<String> rich(ShownRemarks remarks) {
        return rich(DIFF, remarks);
    }

    private static List<String> rich(String diff, ShownRemarks remarks) {
        return text(RichDiff.lines(ParsedDiff.of(diff), false, Option.of(remarks), Theme.SpanStyles.NONE))
                .map(String::stripTrailing);
    }

    @Test
    void aThreadIsABlockAfterItsLineWhichTheGutterMarks() {
        List<String> lines = rich(shown(false, thread(2, false, "Why **upper** case?", "Shouting.")));

        String indent = " ".repeat(8);
        assertEquals(
                List.of(
                        " modified  a.txt",
                        "1 1 │  one",
                        "2   │ -two",
                        "  2 ● +TWO",
                        indent + "┃ bob · 2h ago",
                        indent + "┃ Why upper case?",
                        indent + "┃ bob · 2h ago",
                        indent + "┃ Shouting.",
                        "",
                        "3 3 │  three"),
                lines);
    }

    @Test
    void aResolvedThreadIsOneLineUntilTheThreadsAreOpened() {
        ReviewThread resolved = thread(3, true, "Done\n\nmore");

        assertTrue(rich(shown(false, resolved)).contains(" ".repeat(8) + "┃ ✓ resolved · bob: Done"));
        assertTrue(rich(shown(true, resolved)).contains(" ".repeat(8) + "┃ Done"));
    }

    @Test
    void aThreadOnSeveralLinesMarksThemAndOneThatCannotBePlacedEndsItsFile() {
        ReviewThread several = new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.of(3),
                Option.of(1),
                Side.RIGHT,
                Option.of(3),
                false,
                false,
                Subject.LINE,
                List.of(new Comment("", NOW, "All of it", Option.none())),
                2);
        ReviewThread outdated = new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.none(),
                Option.none(),
                Side.RIGHT,
                Option.of(40),
                false,
                true,
                Subject.LINE,
                List.of(new Comment("carol", NOW, "Old", Option.of("OUTDATED"))),
                0);

        List<String> lines = rich(shown(false, several, outdated));

        assertTrue(lines.contains("1 1 ┃  one"), lines.mkString("\n"));
        assertTrue(lines.contains("3 3 ●  three"), lines.mkString("\n"));
        assertTrue(lines.contains(" ".repeat(8) + "┃ ghost · just now"), lines.mkString("\n"));
        assertTrue(lines.contains(" ".repeat(8) + "┃ 2 more on GitHub"), lines.mkString("\n"));
        assertEquals(
                List.of(
                        " ".repeat(8) + "Not on a line of this diff",
                        " ".repeat(8) + "┃ on line 40, outdated",
                        " ".repeat(8) + "┃ carol · just now",
                        " ".repeat(8) + "┃ hidden · outdated"),
                lines.takeRight(4));
        assertEquals(
                List.of(" ".repeat(8) + "┃ 2 more on GitHub", "", " ".repeat(8) + "Not on a line of this diff"),
                lines.dropRight(3).takeRight(3));
    }

    @Test
    void twoThreadsOnALineHaveABlankLineBetweenThem() {
        List<String> lines = rich(shown(false, thread(2, false, "One"), thread(2, false, "Two")));

        String indent = " ".repeat(8);
        assertEquals(
                List.of(
                        "  2 ● +TWO",
                        indent + "┃ bob · 2h ago",
                        indent + "┃ One",
                        "",
                        indent + "┃ bob · 2h ago",
                        indent + "┃ Two",
                        "",
                        "3 3 │  three"),
                lines.drop(3));
    }

    @Test
    void nothingButTheBlankLineBetweenFilesFollowsAThreadOnTheLastLineOfAFile() {
        String diff = DIFF + """
                diff --git a/b.txt b/b.txt
                index 3333333..4444444 100644
                --- a/b.txt
                +++ b/b.txt
                @@ -1 +1 @@
                -x
                +y
                """;

        List<String> lines =
                rich(diff, shown(false, thread("a.txt", 3, false, "End of a"), thread("b.txt", 1, false, "End of b")));

        String indent = " ".repeat(8);
        assertEquals(
                List.of(
                        "3 3 ●  three",
                        indent + "┃ bob · 2h ago",
                        indent + "┃ End of a",
                        "",
                        " modified  b.txt",
                        "1   │ -x",
                        "  1 ● +y",
                        indent + "┃ bob · 2h ago",
                        indent + "┃ End of b"),
                lines.drop(4));
    }

    @Test
    void aThreadAboutAWholeFileGoesUnderItsBar() {
        ReviewThread onFile = new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.none(),
                Option.none(),
                Side.RIGHT,
                Option.none(),
                false,
                false,
                Subject.FILE,
                List.of(new Comment("bob", NOW.minusSeconds(7200), "Split this file", Option.none())),
                0);

        List<String> lines = rich(shown(false, onFile));

        assertEquals(
                List.of(" modified  a.txt", " ".repeat(8) + "┃ bob · 2h ago", " ".repeat(8) + "┃ Split this file"),
                lines.take(3));
        assertEquals(List.of("", "1 1 │  one"), lines.slice(3, 5));
    }

    @Test
    void aHiddenCommentShowsInFullOnceTheThreadsAreOpened() {
        ReviewThread hidden = new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.of(2),
                Option.none(),
                Side.RIGHT,
                Option.of(2),
                false,
                false,
                Subject.LINE,
                List.of(new Comment("carol", NOW, "Off topic", Option.of("OFF_TOPIC"))),
                0);

        assertTrue(rich(shown(false, hidden)).contains(" ".repeat(8) + "┃ hidden · off-topic"));
        assertFalse(rich(shown(false, hidden)).contains(" ".repeat(8) + "┃ Off topic"));
        assertTrue(rich(shown(true, hidden)).contains(" ".repeat(8) + "┃ Off topic"));
    }

    @Test
    void threadsReadAtOtherCommitsThanTheDiffAreListedAtTheEndOfTheirFile() {
        ShownRemarks elsewhere =
                new ShownRemarks(List.of(thread(2, false, "Moved on")), false, NOW, Option.none(), false);

        List<String> lines = rich(elsewhere);

        assertTrue(lines.contains("  2   +TWO") || lines.contains("  2 │ +TWO"), lines.mkString("\n"));
        assertFalse(lines.exists(line -> line.contains("●")), lines.mkString("\n"));
        assertEquals(
                " ".repeat(8) + "Not on a line of this diff",
                lines.dropWhile(line -> !line.contains("three")).get(1));
        assertTrue(lines.contains(" ".repeat(8) + "┃ Moved on"), lines.mkString("\n"));
    }

    @Test
    void aBlockWrapsAtTheWidthOfItsFileAtLeastSixtyColumns() {
        String words = "word ".repeat(30).strip();

        List<String> lines = rich(shown(false, thread(2, false, words)));

        assertTrue(lines.forAll(line -> TextWidth.of(line) <= RemarkLines.MIN_WIDTH), lines.mkString("\n"));
        assertTrue(lines.count(line -> line.contains("word")) > 1, lines.mkString("\n"));
    }

    @Test
    void markdownIsReadBackFromWhatTamboUiDrawsAsLinesWithoutTheirBlankEnd() {
        List<String> lines = text(
                RemarkLines.markdown("# Title\n\n- one\n- `two`\n\n```\ncode\n```", 30, StylePropertyResolver.empty()));

        assertTrue(lines.exists(line -> line.contains("Title")), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.contains("one")), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.contains("code")), lines.mkString("\n"));
        assertTrue(lines.forAll(line -> line.equals(line.stripTrailing())), lines.mkString("|\n"));
        assertEquals(List.empty(), RemarkLines.markdown("  ", 30, StylePropertyResolver.empty()));
    }

    @Test
    void thePlainViewCountsTheRemarksOfEachFileAtItsStart() {
        Text text = new Text(ContentPanel.diffText(
                new ShownDiff(DiffText.of(DIFF), Option.none()),
                new DiffLook(DiffStyle.PLAIN, true),
                Option.of(shown(false, thread(2, false, "a"), thread(3, true, "b"))),
                Theme.SpanStyles.NONE));

        assertEquals(
                "diff --git a/a.txt b/a.txt  · 2 review remarks, Ctrl+D shows them",
                text.lines().head());
        assertFalse(text.lines().exists(line -> line.contains("bob")));
    }

    @Test
    void whyTheRemarksCouldNotBeReadComesFirst() {
        Text text = new Text(ContentPanel.diffText(
                new ShownDiff(DiffText.of(DIFF), Option.none()),
                new DiffLook(DiffStyle.RICH, true),
                Option.of(new ShownRemarks(
                        List.empty(), false, NOW, Option.of(new Failure.Bug(new RuntimeException("offline"))), true)),
                Theme.SpanStyles.NONE));

        assertTrue(
                text.lines().head().startsWith("The review remarks could not be read"),
                text.lines().head());
    }

    /**
     * The text of styled text, line by line.
     */
    private record Text(dev.tamboui.text.Text styled) {

        List<String> lines() {
            return text(List.ofAll(styled.lines()));
        }
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
