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
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
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
 * columns in, with a rule down its left, and its comments under a header that says who wrote them and when, which
 * the rule branches into and the theme sets apart, their Markdown drawn as the theme styles it.
 */
final class RemarkLines {

    // How wide a block is, its indent included, whatever the width of its file.
    static final int MIN_WIDTH = 60;
    static final int MAX_WIDTH = 100;
    // The narrowest the text of a block gets, past a wide gutter.
    private static final int MIN_TEXT = 20;
    // The rule down the left of a block, which branches into the line that starts it and into each header, and
    // turns at its end; a block of one line has a rule of its own.
    private static final String RULE = "┃  ";
    private static final String TOP = "┏━";
    private static final String BRANCH = "┣━";
    private static final String BOTTOM = "┗━";
    private static final String ALONE = "━━";

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
        return blocks(threads, indent, width, style, styles)
                .intersperse(List.of(Line.from("")))
                .flatMap(Function.identity());
    }

    /**
     * The lines of each of {@code threads}, as {@link #threads} draws them, without the blank lines between them: each
     * is a stop of the cursor (ADR 60).
     */
    static List<List<Line>> blocks(
            List<ReviewThread> threads, int indent, int width, RemarkStyle style, Theme.SpanStyles styles) {
        return threads.map(thread -> thread(thread, indent, width, true, style, styles));
    }

    /**
     * The lines of {@code threads}, which cannot be placed on a line: under a line that says so, each under a header
     * that says which line it was made on, and whether it is outdated, with a blank line between two of them.
     */
    static List<Line> unplaced(
            List<ReviewThread> threads, int indent, int width, RemarkStyle style, Theme.SpanStyles styles) {
        return threads.isEmpty()
                ? List.empty()
                : unplacedBlocks(threads, indent, width, style, styles)
                        .intersperse(List.of(Line.from("")))
                        .flatMap(Function.identity())
                        .prepend(unplacedHeading(indent, styles));
    }

    /**
     * The line that says the threads under it cannot be placed.
     */
    static Line unplacedHeading(int indent, Theme.SpanStyles styles) {
        return Line.from(
                Span.raw(" ".repeat(indent)),
                styled(Messages.get("content.remarks.unplaced"), Theme.Classes.DIM, styles));
    }

    /**
     * The lines of each of {@code threads}, which cannot be placed, as {@link #unplaced} draws them under its heading,
     * without the blank lines between them: each is a stop of the cursor (ADR 60).
     */
    static List<List<Line>> unplacedBlocks(
            List<ReviewThread> threads, int indent, int width, RemarkStyle style, Theme.SpanStyles styles) {
        Span margin = Span.raw(" ".repeat(indent));
        return threads.map(thread -> thread(thread, indent, width, false, style, styles)
                .prepend(header(
                        List.of(margin, styled(TOP, Theme.Classes.DIFF_THREAD, styles)),
                        List.of(styled(madeOn(thread), Theme.Classes.DIM, styles)),
                        text(indent, width),
                        styles)));
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

    /**
     * The lines of {@code thread}, its first header the {@code top} of its block, or under a line above it, and a line
     * where its rule turns at the end. A resolved thread while the threads are closed is one line, which ends the
     * rule of the line above it, or has a rule of its own.
     */
    private static List<Line> thread(
            ReviewThread thread, int indent, int width, boolean top, RemarkStyle style, Theme.SpanStyles styles) {
        Span margin = Span.raw(" ".repeat(indent));
        List<Span> rule = List.of(margin, styled(RULE, Theme.Classes.DIFF_THREAD, styles));
        int text = text(indent, width);
        boolean open = style.opens(thread);
        // While a reply or its resolving is sent, it says so (ADR 60).
        List<Span> sending = style.sends(thread)
                ? List.of(styled(" · " + Messages.get("content.remarks.sending"), Theme.Classes.DIM, styles))
                : List.empty();
        if (thread.resolved() && !open) {
            String first =
                    thread.comments().headOption().map(RemarkLines::firstLine).getOrElse("");
            String author = thread.comments()
                    .headOption()
                    .map(comment -> author(comment.author()))
                    .getOrElse("");
            return List.of(Line.from(List.of(
                            margin,
                            styled((top ? ALONE : BOTTOM) + " ", Theme.Classes.DIFF_THREAD, styles),
                            styled(
                                    cut(Messages.get("content.remarks.resolved", author, first), text),
                                    Theme.Classes.DIM,
                                    styles))
                    .appendAll(sending)
                    .toJavaList()));
        }
        // A line of only the rule between two comments, to show where the next one starts.
        return thread.comments()
                .zipWithIndex()
                .map(comment -> comment(
                        comment._1,
                        List.of(
                                margin,
                                styled(top && comment._2 == 0 ? TOP : BRANCH, Theme.Classes.DIFF_THREAD, styles)),
                        rule,
                        text,
                        open,
                        style.now(),
                        styles))
                .intersperse(List.of(Line.from(margin, styled(RULE.strip(), Theme.Classes.DIFF_THREAD, styles))))
                .flatMap(Function.identity())
                .appendAll(
                        thread.more() > 0
                                ? List.of(Line.from(rule.append(styled(
                                                Messages.get("content.remarks.more", String.valueOf(thread.more())),
                                                Theme.Classes.DIM,
                                                styles))
                                        .toJavaList()))
                                : List.empty())
                .appendAll(
                        sending.isEmpty()
                                ? List.empty()
                                : List.of(Line.from(rule.append(styled(
                                                Messages.get("content.remarks.sending"), Theme.Classes.DIM, styles))
                                        .toJavaList())))
                .append(Line.from(margin, styled(BOTTOM, Theme.Classes.DIFF_THREAD, styles)));
    }

    /**
     * A comment: a header after {@code branch} that says who wrote it and how long ago, on the background of
     * {@link Theme.Classes#DIFF_THREAD_HEADER} to the end of the block, then its text, or why it is hidden while its
     * thread is not {@code open}; how long ago it was written, at {@code now}.
     */
    private static List<Line> comment(
            Comment comment,
            List<Span> branch,
            List<Span> rule,
            int text,
            boolean open,
            Instant now,
            Theme.SpanStyles styles) {
        String author = author(comment.author());
        String ago = Messages.get("content.remarks.ago", TimeText.ago(Duration.between(comment.at(), now)));
        Line header = header(
                branch,
                List.of(
                        styled(author, Theme.Classes.DIFF_THREAD_AUTHOR, styles),
                        styled(ago, Theme.Classes.DIM, styles)),
                text,
                styles);
        List<Line> body = comment.hidden()
                .filter(hidden -> !open)
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

    /**
     * How wide the text of a block is that starts {@code indent} columns in and is {@code width} wide, past its rule.
     */
    private static int text(int indent, int width) {
        return Math.max(MIN_TEXT, width - indent - RULE.length());
    }

    /**
     * A header: {@code content} after {@code branch}, on the background of {@link Theme.Classes#DIFF_THREAD_HEADER}
     * to the end of the block, {@code text} columns past the rule.
     */
    private static Line header(List<Span> branch, List<Span> content, int text, Theme.SpanStyles styles) {
        Style band = styles.of(Theme.Classes.DIFF_THREAD_HEADER);
        int used = content.map(Span::width).sum().intValue();
        return Line.from(branch.append(Span.styled(" ", band))
                .appendAll(content.map(span -> Span.styled(span.content(), onBand(span.style(), band))))
                .append(Span.styled(" ".repeat(Math.max(0, text - used)), band))
                .toJavaList());
    }

    /**
     * {@code style} on the background of {@code band}, if it has one.
     */
    private static Style onBand(Style style, Style band) {
        return band.bg().map(style::bg).orElse(style);
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
     *
     * @param opened  whether every thread is open
     * @param toggled the ids of the threads opened or closed against {@code opened} (ADR 60)
     * @param sending the ids of the threads a reply or a resolving is sent for (ADR 60)
     * @param now     the time it is
     */
    record RemarkStyle(boolean opened, Set<String> toggled, Set<String> sending, Instant now) {

        RemarkStyle(boolean opened, Instant now) {
            this(opened, HashSet.empty(), HashSet.empty(), now);
        }

        /**
         * Whether {@code thread} is shown open: its resolved state and its hidden comments too.
         */
        boolean opens(ReviewThread thread) {
            return opened != toggled.contains(thread.id());
        }

        /**
         * Whether something is sent for {@code thread}.
         */
        boolean sends(ReviewThread thread) {
            return sending.contains(thread.id());
        }
    }
}
