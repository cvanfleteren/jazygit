package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.style.StylePropertyResolver;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.Collapsed;
import net.vanfleteren.jazygit.state.DiffStops;
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
                List.of(bodies)
                        .zipWithIndex((body, at) ->
                                new Comment("bob", NOW.minusSeconds(7200), body, Option.none(), "C" + at, "1")),
                0,
                path + line + String.join("", bodies),
                ReviewThread.Rights.ALL);
    }

    private static ShownRemarks shown(Collapsed collapsed, ReviewThread... threads) {
        return new ShownRemarks(
                List.of(threads), NOW, Option.none(), true, collapsed, io.vavr.collection.HashSet.empty());
    }

    private static List<String> rich(ShownRemarks remarks) {
        return rich(DIFF, remarks);
    }

    private static List<String> rich(String diff, ShownRemarks remarks) {
        return text(drawn(ParsedDiff.of(diff), false, Option.of(remarks), Theme.SpanStyles.NONE)
                        .lines())
                .map(String::stripTrailing);
    }

    @Test
    void aThreadIsABlockAfterItsLineWhichTheGutterMarks() {
        List<String> lines = rich(shown(Collapsed.NONE, thread(2, false, "Why **upper** case?", "Shouting.")));

        String indent = " ".repeat(8);
        assertEquals(
                List.of(
                        " modified  a.txt",
                        "1 1 │  one",
                        "2   │ -two",
                        "  2 ● +TWO",
                        indent + "┏━ bob · 2h ago",
                        indent + "┃  Why upper case?",
                        indent + "┃",
                        indent + "┣━ bob · 2h ago",
                        indent + "┃  Shouting.",
                        indent + "┗━",
                        "",
                        "3 3 │  three"),
                lines);
    }

    @Test
    void theHeaderOfACommentIsOnTheBandOfTheThemeToTheEndOfTheBlock() {
        Theme.SpanStyles styles = cssClass -> switch (cssClass) {
            case Theme.Classes.DIFF_THREAD_HEADER -> Style.EMPTY.bg(Color.BLUE);
            case Theme.Classes.DIFF_THREAD_AUTHOR -> Style.EMPTY.fg(Color.CYAN);
            default -> Style.EMPTY;
        };

        Line header = RemarkLines.threads(
                        List.of(thread(2, false, "Why?")),
                        8,
                        RemarkLines.MIN_WIDTH,
                        new RemarkLines.RemarkStyle(NOW),
                        styles)
                .head();

        List<Span> band = List.ofAll(header.spans()).drop(2);
        assertEquals(RemarkLines.MIN_WIDTH, header.width());
        assertTrue(band.forAll(span -> span.style().bg().equals(Optional.of(Color.BLUE))), header.toString());
        assertTrue(band.exists(
                span -> span.content().equals("bob") && span.style().fg().equals(Optional.of(Color.CYAN))));
    }

    private static List<String> drawnAlone(
            ReviewThread thread, Collapsed collapsed, io.vavr.collection.Set<String> sending) {
        return text(RemarkLines.threads(
                        List.of(thread),
                        8,
                        RemarkLines.MIN_WIDTH,
                        new RemarkLines.RemarkStyle(collapsed, sending, NOW),
                        Theme.SpanStyles.NONE))
                .map(String::stripTrailing);
    }

    @Test
    void aThreadIsCollapsedOrOpenedAgainstItsDefaultAndSaysWhenSomethingIsSentForIt() {
        ReviewThread resolved = thread(3, true, "Done");
        ReviewThread open = thread(2, false, "Why?", "Because");
        String indent = " ".repeat(8);

        assertTrue(drawnAlone(resolved, Collapsed.NONE.flipped(resolved.id()), HashSet.empty())
                .contains(indent + "┃  Done"));
        assertEquals(
                List.of(indent + "━━ ✓ resolved · bob: Done"), drawnAlone(resolved, Collapsed.NONE, HashSet.empty()));
        assertEquals(
                List.of(indent + "━━ ✓ resolved · bob: Done · sending…"),
                drawnAlone(resolved, Collapsed.NONE, HashSet.of(resolved.id())));

        // An unresolved thread collapsed says how many comments it has, not dimmed (ADR 61).
        Collapsed closed = Collapsed.NONE.flipped(open.id());
        assertEquals(List.of(indent + "━━ ▸ 2 remarks · bob: Why?"), drawnAlone(open, closed, HashSet.empty()));
        assertEquals(
                List.of(indent + "━━ ▸ 2 remarks · bob: Why? · sending…"),
                drawnAlone(open, closed, HashSet.of(open.id())));
        Theme.SpanStyles dim =
                cssClass -> cssClass.equals(Theme.Classes.DIM) ? Style.EMPTY.fg(Color.GRAY) : Style.EMPTY;
        Line line = RemarkLines.threads(
                        List.of(open),
                        8,
                        RemarkLines.MIN_WIDTH,
                        new RemarkLines.RemarkStyle(closed, HashSet.empty(), NOW),
                        dim)
                .head();
        assertTrue(List.ofAll(line.spans()).forAll(span -> span.style().equals(Style.EMPTY)), line.toString());

        List<String> replying = drawnAlone(open, Collapsed.NONE, HashSet.of(open.id()));
        assertEquals(indent + "┃  sending…", replying.get(replying.size() - 2));
    }

    @Test
    void aCommentButTheFirstIsCollapsedToItsHeaderAndItsFirstLine() {
        ReviewThread open = thread(2, false, "Why?", "Because\n\nreally");
        String indent = " ".repeat(8);

        assertEquals(
                List.of(
                        indent + "┏━ bob · 2h ago",
                        indent + "┃  Why?",
                        indent + "┃",
                        indent + "┣━ bob · 2h ago · Because",
                        indent + "┗━"),
                drawnAlone(open, Collapsed.NONE.flipped("C1"), HashSet.empty()));
        // The first comment is the thread: flipping its id does nothing.
        assertEquals(
                drawnAlone(open, Collapsed.NONE, HashSet.empty()),
                drawnAlone(open, Collapsed.NONE.flipped("C0"), HashSet.empty()));
    }

    @Test
    void aHiddenCommentIsCollapsedUntilItIsOpened() {
        ReviewThread thread = thread(2, false, "Why?");
        ReviewThread hidden = new ReviewThread(
                thread.path(),
                thread.side(),
                thread.line(),
                thread.startLine(),
                thread.startSide(),
                thread.originalLine(),
                false,
                false,
                thread.subject(),
                thread.comments()
                        .append(new Comment("carol", NOW.minusSeconds(7200), "Buy now", Option.of("SPAM"), "H", "2")),
                2,
                thread.id(),
                thread.rights());
        String indent = " ".repeat(8);

        List<String> collapsed = drawnAlone(hidden, Collapsed.NONE, HashSet.empty());
        assertTrue(collapsed.contains(indent + "┣━ carol · 2h ago · hidden · spam"), collapsed.mkString("\n"));
        assertFalse(collapsed.exists(line -> line.contains("Buy now")));
        assertTrue(
                drawnAlone(hidden, Collapsed.NONE.flipped("H"), HashSet.empty()).contains(indent + "┃  Buy now"));
        // The count of a collapsed thread is of the comments GitHub has more of too.
        assertEquals(
                List.of(indent + "━━ ▸ 4 remarks · bob: Why?"),
                drawnAlone(hidden, Collapsed.NONE.flipped(hidden.id()), HashSet.empty()));
    }

    @Test
    void aResolvedThreadIsOneLineUntilItIsOpened() {
        ReviewThread resolved = thread(3, true, "Done\n\nmore");

        assertTrue(rich(shown(Collapsed.NONE, resolved)).contains(" ".repeat(8) + "━━ ✓ resolved · bob: Done"));
        assertEquals(
                List.of(" ".repeat(8) + "┏━ on line 3", " ".repeat(8) + "┗━ ✓ resolved · bob: Done"),
                text(RemarkLines.unplaced(
                                List.of(resolved),
                                8,
                                RemarkLines.MIN_WIDTH,
                                new RemarkLines.RemarkStyle(NOW),
                                Theme.SpanStyles.NONE))
                        .tail()
                        .map(String::stripTrailing));
        assertTrue(rich(shown(Collapsed.NONE.flipped(resolved.id()), resolved)).contains(" ".repeat(8) + "┃  Done"));
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
                List.of(new Comment("", NOW, "All of it", Option.none(), "C", "1")),
                2,
                "T",
                ReviewThread.Rights.ALL);
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
                List.of(new Comment("carol", NOW, "Old", Option.of("OUTDATED"), "C", "1")),
                0,
                "T",
                ReviewThread.Rights.ALL);

        List<String> lines = rich(shown(Collapsed.NONE, several, outdated));

        assertTrue(lines.contains("1 1 ┃  one"), lines.mkString("\n"));
        assertTrue(lines.contains("3 3 ●  three"), lines.mkString("\n"));
        assertTrue(lines.contains(" ".repeat(8) + "┏━ ghost · just now"), lines.mkString("\n"));
        assertTrue(lines.contains(" ".repeat(8) + "┃  2 more on GitHub"), lines.mkString("\n"));
        assertEquals(
                List.of(
                        " ".repeat(8) + "Not on a line of this diff",
                        " ".repeat(8) + "┏━ on line 40, outdated",
                        " ".repeat(8) + "┗━ ▸ 1 remark · carol: hidden · outdated"),
                lines.takeRight(3));
        assertEquals(
                List.of(
                        " ".repeat(8) + "┃  2 more on GitHub",
                        " ".repeat(8) + "┗━",
                        "",
                        " ".repeat(8) + "Not on a line of this diff"),
                lines.dropRight(2).takeRight(4));
    }

    @Test
    void twoThreadsOnALineHaveABlankLineBetweenThem() {
        List<String> lines = rich(shown(Collapsed.NONE, thread(2, false, "One"), thread(2, false, "Two")));

        String indent = " ".repeat(8);
        assertEquals(
                List.of(
                        "  2 ● +TWO",
                        indent + "┏━ bob · 2h ago",
                        indent + "┃  One",
                        indent + "┗━",
                        "",
                        indent + "┏━ bob · 2h ago",
                        indent + "┃  Two",
                        indent + "┗━",
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

        List<String> lines = rich(
                diff,
                shown(Collapsed.NONE, thread("a.txt", 3, false, "End of a"), thread("b.txt", 1, false, "End of b")));

        String indent = " ".repeat(8);
        assertEquals(
                List.of(
                        "3 3 ●  three",
                        indent + "┏━ bob · 2h ago",
                        indent + "┃  End of a",
                        indent + "┗━",
                        "",
                        " modified  b.txt",
                        "1   │ -x",
                        "  1 ● +y",
                        indent + "┏━ bob · 2h ago",
                        indent + "┃  End of b",
                        indent + "┗━"),
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
                List.of(new Comment("bob", NOW.minusSeconds(7200), "Split this file", Option.none(), "C", "1")),
                0,
                "T",
                ReviewThread.Rights.ALL);

        List<String> lines = rich(shown(Collapsed.NONE, onFile));

        assertEquals(
                List.of(" modified  a.txt", " ".repeat(8) + "┏━ bob · 2h ago", " ".repeat(8) + "┃  Split this file"),
                lines.take(3));
        assertEquals(List.of(" ".repeat(8) + "┗━", "", "1 1 │  one"), lines.slice(3, 6));
    }

    @Test
    void aThreadWhoseFirstCommentIsHiddenShowsItInFullOnceItIsOpened() {
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
                List.of(new Comment("carol", NOW, "Off topic", Option.of("OFF_TOPIC"), "C", "1")),
                0,
                "T",
                ReviewThread.Rights.ALL);

        // Its first comment hidden, the thread is collapsed, and says why in place of its first line (ADR 61).
        List<String> collapsed = rich(shown(Collapsed.NONE, hidden));
        assertTrue(
                collapsed.contains(" ".repeat(8) + "━━ ▸ 1 remark · carol: hidden · off-topic"),
                collapsed.mkString("\n"));
        assertFalse(collapsed.exists(line -> line.contains("Off topic")));
        assertTrue(rich(shown(Collapsed.NONE.flipped("T"), hidden)).contains(" ".repeat(8) + "┃  Off topic"));
    }

    @Test
    void threadsReadAtOtherCommitsThanTheDiffAreListedAtTheEndOfTheirFile() {
        ShownRemarks elsewhere = new ShownRemarks(
                List.of(thread(2, false, "Moved on")),
                NOW,
                Option.none(),
                false,
                Collapsed.NONE,
                io.vavr.collection.HashSet.empty());

        List<String> lines = rich(elsewhere);

        assertTrue(lines.contains("  2   +TWO") || lines.contains("  2 │ +TWO"), lines.mkString("\n"));
        assertFalse(lines.exists(line -> line.contains("●")), lines.mkString("\n"));
        assertEquals(
                " ".repeat(8) + "Not on a line of this diff",
                lines.dropWhile(line -> !line.contains("three")).get(1));
        assertTrue(lines.contains(" ".repeat(8) + "┃  Moved on"), lines.mkString("\n"));
    }

    @Test
    void threadsThatCannotBePlacedHaveABlankLineBetweenThemAndAHeaderOnTheBand() {
        Theme.SpanStyles styles = cssClass ->
                cssClass.equals(Theme.Classes.DIFF_THREAD_HEADER) ? Style.EMPTY.bg(Color.BLUE) : Style.EMPTY;

        List<Line> lines = RemarkLines.unplaced(
                List.of(thread(40, false, "Old"), thread(50, false, "Older")),
                8,
                RemarkLines.MIN_WIDTH,
                new RemarkLines.RemarkStyle(NOW),
                styles);

        String indent = " ".repeat(8);
        assertEquals(
                List.of(
                        indent + "Not on a line of this diff",
                        indent + "┏━ on line 40",
                        indent + "┣━ bob · 2h ago",
                        indent + "┃  Old",
                        indent + "┗━",
                        "",
                        indent + "┏━ on line 50",
                        indent + "┣━ bob · 2h ago",
                        indent + "┃  Older",
                        indent + "┗━"),
                text(lines).map(String::stripTrailing));
        assertEquals(RemarkLines.MIN_WIDTH, lines.get(1).width());
        assertTrue(
                List.ofAll(lines.get(1).spans())
                        .drop(2)
                        .forAll(span -> span.style().bg().equals(Optional.of(Color.BLUE))),
                lines.get(1).toString());
    }

    @Test
    void aBlockWrapsAtTheWidthOfItsFileAtLeastSixtyColumns() {
        String words = "word ".repeat(30).strip();

        List<String> lines = rich(shown(Collapsed.NONE, thread(2, false, words)));

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
                new DiffLook(DiffStyle.PLAIN, true, true),
                Option.of(shown(Collapsed.NONE, thread(2, false, "a"), thread(3, true, "b"))),
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
                new DiffLook(DiffStyle.RICH, true, true),
                Option.of(new ShownRemarks(
                        List.empty(),
                        NOW,
                        Option.of(new Failure.Bug(new RuntimeException("offline"))),
                        true,
                        Collapsed.NONE,
                        io.vavr.collection.HashSet.empty())),
                Theme.SpanStyles.NONE));

        assertTrue(
                text.lines().head().startsWith("The review remarks could not be read"),
                text.lines().head());
    }

    @Test
    void theCursorStopsWhereDiffStopsSaysInTheOrderTheyAreDrawn() {
        ReviewThread first = thread(2, false, "a");
        ReviewThread second = thread(2, false, "b", "c");
        ReviewThread outdated = thread("a.txt", 40, false, "old");
        ShownRemarks remarks = shown(Collapsed.NONE, first, second, outdated);

        RichDiff.Drawn drawn = drawn(ParsedDiff.of(DIFF), true, Option.of(remarks), Theme.SpanStyles.NONE);

        // Nothing comes before the file, so there is no lead.
        assertEquals(
                DiffStops.of(UnifiedDiff.parse(DIFF), remarks.threads(), true, remarks.collapsed())
                        .anchors()
                        .tail(),
                drawn.stops().map(stop -> stop.mark().anchor()));
        List<String> lines = text(drawn.lines());
        List<Integer> rows = drawn.stops().map(RichDiff.Stop::row);
        assertEquals(rows.sorted().distinct(), rows);
        // Each thread starts on its own first line, and has as many lines as it is drawn with.
        List<RichDiff.Stop> threads = drawn.stops().filter(stop -> stop.mark().anchor() instanceof Anchor.Thread);
        assertEquals(3, threads.size());
        assertTrue(threads.forAll(stop -> lines.get(stop.row()).contains("┏━")), lines.mkString("\n"));
        assertTrue(lines.get(threads.head().row() + threads.head().mark().lines() - 1)
                .contains("┗━"));
        assertTrue(
                lines.get(threads.head().row() + threads.head().mark().lines()).isBlank());
        // A comment but the first starts its own stop, which ends its thread after the last (ADR 61).
        RichDiff.Stop comment = drawn.stops()
                .find(stop -> stop.mark().anchor() instanceof Anchor.Comment)
                .get();
        assertEquals(
                new Anchor.Comment(
                        "C1", new Anchor.Thread(second.id(), Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 2)))),
                comment.mark().anchor());
        assertTrue(lines.get(comment.row()).contains("┣━ bob"), lines.mkString("\n"));
        assertTrue(lines.get(comment.row() + comment.mark().lines() - 1).contains("┗━"));
    }

    @Test
    void theLeadIsWhatComesBeforeTheFirstStop() {
        RichDiff.Drawn drawn = RichDiff.Drawn.plain(List.of(Line.from("commit"), Line.from("---")))
                .withLead();
        assertEquals(List.empty(), drawn.stops());

        RichDiff.Drawn lead = drawn(ParsedDiff.of(DIFF), true, Option.none(), Theme.SpanStyles.NONE)
                .after(List.of(Line.from("commit"), Line.from("---")))
                .withLead();
        assertEquals(new Anchor.Lead(), lead.stops().head().mark().anchor());
        assertEquals(new Anchor.File("a.txt"), lead.stops().get(1).mark().anchor());
        assertEquals(2, lead.stops().get(1).row());
    }

    /**
     * The text of styled text, line by line.
     */
    private record Text(dev.tamboui.text.Text styled) {

        List<String> lines() {
            return text(List.ofAll(styled.lines()));
        }
    }

    /**
     * {@code diff} drawn with the review threads {@code remarks}, the changed words highlighted.
     */
    private static RichDiff.Drawn drawn(
            ParsedDiff diff, boolean syntax, Option<ShownRemarks> remarks, Theme.SpanStyles styles) {
        return RichDiff.drawn(diff, syntax, true, remarks, styles);
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
