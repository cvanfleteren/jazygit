package net.vanfleteren.jazygit.ui;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Style;
import dev.tamboui.style.StylePropertyResolver;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.MarkdownScroll;

/**
 * The review threads of a pull request drawn as lines of its rich diff (ADR 55): each a block that starts some
 * columns in, with a rule down its left, and its comments under a line that says who wrote them and when, their
 * Markdown drawn as the theme styles it.
 */
final class RemarkLines {

    // How wide a block is, its indent included, whatever the width of its file.
    static final int MIN_WIDTH = 60;
    static final int MAX_WIDTH = 100;
    // The narrowest the text of a block gets, past a wide gutter.
    private static final int MIN_TEXT = 20;
    private static final String RULE = "┃ ";

    private RemarkLines() {}

    /**
     * How wide the blocks of a file {@code width} columns wide are, their indent included.
     */
    static int blockWidth(int width) {
        return Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
    }

    /**
     * The lines of {@code threads}, as blocks that start {@code indent} columns in and are {@code width} wide, with a
     * blank line between two of them.
     */
    static List<Line> threads(
            List<ReviewThread> threads, int indent, int width, RemarkStyle style, Theme.SpanStyles styles) {
        return threads.map(thread -> thread(thread, indent, width, style, styles))
                .intersperse(List.of(Line.from("")))
                .flatMap(Function.identity());
    }

    /**
     * The lines of {@code threads}, which cannot be placed on a line: under a line that says so, each under a line that
     * says which line it was made on, and whether it is outdated.
     */
    static List<Line> unplaced(
            List<ReviewThread> threads, int indent, int width, RemarkStyle style, Theme.SpanStyles styles) {
        return threads.isEmpty()
                ? List.empty()
                : threads.flatMap(thread -> thread(thread, indent, width, style, styles)
                                .prepend(Line.from(
                                        Span.raw(" ".repeat(indent)),
                                        styled(RULE, Theme.Classes.DIFF_THREAD, styles),
                                        styled(madeOn(thread), Theme.Classes.DIM, styles))))
                        .prepend(Line.from(
                                Span.raw(" ".repeat(indent)),
                                styled(Messages.get("content.remarks.unplaced"), Theme.Classes.DIM, styles)));
    }

    /**
     * Which line {@code thread} was made on, and whether it is outdated.
     */
    private static String madeOn(ReviewThread thread) {
        String line = thread.line()
                .orElse(thread::originalLine)
                .map(number -> Messages.get("content.remarks.line", String.valueOf(number)))
                .getOrElse(() -> Messages.get("content.remarks.noLine"));
        return thread.outdated() ? Messages.get("content.remarks.outdated", line) : line;
    }

    private static List<Line> thread(
            ReviewThread thread, int indent, int width, RemarkStyle style, Theme.SpanStyles styles) {
        List<Span> rule = List.of(Span.raw(" ".repeat(indent)), styled(RULE, Theme.Classes.DIFF_THREAD, styles));
        int text = Math.max(MIN_TEXT, width - indent - RULE.length());
        if (thread.resolved() && !style.opened()) {
            String first =
                    thread.comments().headOption().map(RemarkLines::firstLine).getOrElse("");
            String author = thread.comments()
                    .headOption()
                    .map(comment -> author(comment.author()))
                    .getOrElse("");
            return List.of(Line.from(rule.append(styled(
                            cut(Messages.get("content.remarks.resolved", author, first), text),
                            Theme.Classes.DIM,
                            styles))
                    .toJavaList()));
        }
        // A line of only the rule between two comments, to show where the next one starts.
        return thread.comments()
                .map(comment -> comment(comment, rule, text, style, styles))
                .intersperse(List.of(Line.from(rule.toJavaList())))
                .flatMap(Function.identity())
                .appendAll(
                        thread.more() > 0
                                ? List.of(Line.from(rule.append(styled(
                                                Messages.get("content.remarks.more", String.valueOf(thread.more())),
                                                Theme.Classes.DIM,
                                                styles))
                                        .toJavaList()))
                                : List.empty());
    }

    /**
     * A comment: who wrote it and how long ago, then its text, or why it is hidden while it is closed.
     */
    private static List<Line> comment(
            Comment comment, List<Span> rule, int text, RemarkStyle style, Theme.SpanStyles styles) {
        Line header = Line.from(rule.appendAll(List.of(
                        styled(author(comment.author()), Theme.Classes.BOLD, styles),
                        styled(
                                Messages.get(
                                        "content.remarks.ago",
                                        TimeText.ago(Duration.between(comment.at(), style.now()))),
                                Theme.Classes.DIM,
                                styles)))
                .toJavaList());
        List<Line> body = comment.hidden()
                .filter(hidden -> !style.opened())
                .map(reason -> List.of(Line.from(rule.append(styled(
                                cut(
                                        Messages.get(
                                                "content.remarks.hidden",
                                                reason.toLowerCase(Locale.ROOT).replace('_', '-')),
                                        text),
                                Theme.Classes.DIM,
                                styles))
                        .toJavaList())))
                .getOrElse(() -> markdown(MarkdownScroll.withoutHtmlComments(comment.body()), text, styles.markdown())
                        .map(line -> Line.from(rule.appendAll(line.spans()).toJavaList())));
        return body.prepend(header);
    }

    /**
     * {@code markdown} drawn {@code width} columns wide, as tamboui draws it into a buffer off the screen, its cells
     * read back as lines of spans: each run of cells of one style a span, without the blank cells at the end of a
     * line. It is as high as tamboui measures it; empty text has no lines.
     */
    static List<Line> markdown(String markdown, int width, StylePropertyResolver resolver) {
        if (markdown.isBlank()) {
            return List.empty();
        }
        MarkdownView view =
                MarkdownView.builder().source(markdown).styleResolver(resolver).build();
        int height = view.computeHeight(width);
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        view.render(area, buffer);
        return List.range(0, height).map(row -> row(buffer, row, width));
    }

    private static Line row(Buffer buffer, int row, int width) {
        Vector<Cell> cells =
                Vector.range(0, width).map(column -> buffer.get(column, row)).filter(cell -> !cell.isContinuation());
        int used = cells.lastIndexWhere(
                        cell -> !cell.symbol().isBlank() || cell.style().bg().isPresent())
                + 1;
        List<Tuple2<Style, String>> runs = cells.take(used)
                .foldLeft(
                        List.<Tuple2<Style, String>>empty(),
                        (done, cell) -> done.headOption()
                                .filter(last -> last._1().equals(cell.style()))
                                .map(last -> done.tail().prepend(Tuple.of(last._1(), last._2() + cell.symbol())))
                                .getOrElse(() -> done.prepend(Tuple.of(cell.style(), cell.symbol()))))
                .reverse();
        return Line.from(runs.map(run -> Span.styled(run._2(), run._1())).toJavaList());
    }

    /**
     * The first line of {@code comment} with text, without its HTML comments.
     */
    private static String firstLine(Comment comment) {
        return List.ofAll(MarkdownScroll.withoutHtmlComments(comment.body()).lines())
                .map(String::strip)
                .find(line -> !line.isEmpty())
                .getOrElse("");
    }

    private static String author(String login) {
        return login.isEmpty() ? Messages.get("content.remarks.ghost") : login;
    }

    /**
     * {@code text} cut to {@code width} columns, with an ellipsis when it is cut.
     */
    private static String cut(String text, int width) {
        return TextWidth.cut(text, width);
    }

    private static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
        return Span.styled(text, styles.of(cssClass));
    }

    /**
     * How the threads are drawn: open or not, and the time it is, to tell how long ago a comment was written.
     */
    record RemarkStyle(boolean opened, Instant now) {}
}
