package net.vanfleteren.jazygit.ui;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.DiffStops;
import net.vanfleteren.jazygit.state.PlacedRemarks;
import net.vanfleteren.jazygit.state.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.syntax.Token;

/**
 * A unified diff drawn rich (ADR 44): what comes before its first file as it is, then each file under a bar that
 * says what happened to it, its lines numbered in the old and the new file, and the words that changed in a changed
 * line highlighted.
 */
final class RichDiff {

    private static final String GUTTER_END = " │ ";
    private static final LineRemarks NO_REMARKS = new LineRemarks(List.empty(), false);
    private static final Pending BLANK = () -> List.of(Marked.plain(Line.from("")));

    private RichDiff() {}

    /**
     * The lines of {@code diff}, styled, its code coloured by its syntax if {@code syntax} (ADR 53). It was parsed,
     * its words compared and its code lexed when it was read (ADR 50), so drawing it only styles it.
     */
    static List<Line> lines(ParsedDiff diff, boolean syntax, Theme.SpanStyles styles) {
        return lines(diff, syntax, Option.none(), styles);
    }

    /**
     * The lines of {@code diff}, as {@link #lines(ParsedDiff, boolean, Theme.SpanStyles)} draws them, with the review
     * threads of a pull request, {@code remarks}, if it has them (ADR 55): each after its line, which the gutter marks,
     * one about a whole file under its bar, and the ones that cannot be placed at the end of their file.
     */
    static List<Line> lines(
            ParsedDiff diff, boolean syntax, Option<ContentView.ShownRemarks> remarks, Theme.SpanStyles styles) {
        return drawn(diff, syntax, remarks, styles).lines();
    }

    /**
     * The lines of {@code diff}, as {@link #lines(ParsedDiff, boolean, Option, Theme.SpanStyles)} draws them, with
     * where each stop of the cursor starts in them (ADR 60): the bar of each file, each line that has a side and a
     * number, and each thread.
     */
    static Drawn drawn(
            ParsedDiff diff, boolean syntax, Option<ContentView.ShownRemarks> remarks, Theme.SpanStyles styles) {
        return drawn(diff, syntax, true, remarks, styles);
    }

    /**
     * The lines of {@code diff}, as {@link #drawn(ParsedDiff, boolean, Option, Theme.SpanStyles)} draws them, with the
     * words that changed in a changed line highlighted if {@code words}; else the whole line is in its line class.
     */
    static Drawn drawn(
            ParsedDiff diff,
            boolean syntax,
            boolean words,
            Option<ContentView.ShownRemarks> remarks,
            Theme.SpanStyles styles) {
        UnifiedDiff parsed = diff.diff();
        Vector<Option<FileRemarks>> placed = remarks.map(
                        shown -> PlacedRemarks.place(parsed, shown.threads(), shown.onLines())
                                .files()
                                .map(Option::some)
                                .toVector())
                .getOrElse(() -> Vector.fill(parsed.files().size(), Option::none));
        RemarkLines.RemarkStyle style = remarks.map(shown ->
                        new RemarkLines.RemarkStyle(shown.opened(), shown.toggled(), shown.sending(), shown.now()))
                .getOrElse(() -> new RemarkLines.RemarkStyle(false, Instant.EPOCH));
        // Each file is measured on a thread of its own, and then each of its lines is drawn on one, so that a diff of
        // many files and one of a long file both hold up the render thread for less.
        List<Pending> pending = inParallel(
                        diff.files().zip(placed).zipWithIndex(),
                        file -> file(file._1()._1(), file._1()._2(), file._2(), syntax, words, style, styles))
                .intersperse(List.of(BLANK))
                .flatMap(file -> file);
        return Drawn.of(parsed.preamble()
                .map(line -> Marked.plain(Line.from(Span.raw(line))))
                .appendAll(inParallel(pending, Pending::draw).flatMap(lines -> lines)));
    }

    /**
     * What {@code draw} gives for each of {@code items}, in their order, each worked out on a thread of the common
     * pool. Drawing is pure, and the render thread waits for it, so nothing changes the theme while it runs.
     */
    private static <T, R> List<R> inParallel(List<T> items, Function<T, R> draw) {
        // Collected, not read through an iterator as List.ofAll and Stream.ofAll read a java.util.stream.Stream: that
        // runs a parallel stream on the calling thread only.
        return items.toJavaParallelStream().map(draw).collect(List.collector());
    }

    /**
     * {@code all} cut, in order, into parts of {@code sizes}.
     */
    private static <T> Vector<Vector<T>> cut(Vector<T> all, List<Integer> sizes) {
        return sizes.foldLeft(
                        Tuple.of(Vector.<Vector<T>>empty(), 0),
                        (done, size) -> Tuple.of(done._1.append(all.slice(done._2, done._2 + size)), done._2 + size))
                ._1;
    }

    /**
     * The lines still to be drawn of {@code parsed}, with the review threads {@code remarks} on it, its code coloured
     * by its syntax if {@code syntax}, its changed words highlighted if {@code words}, its threads drawn as {@code style} says: its bar, the threads on it, each of its
     * lines with those after it, once the width of its gutter and its lines is known, and the threads that cannot be
     * placed. The threads on a line are followed by a blank line, unless nothing follows them in the file.
     */
    private static List<Pending> file(
            ParsedFile parsed,
            Option<FileRemarks> remarks,
            int index,
            boolean syntax,
            boolean words,
            RemarkLines.RemarkStyle style,
            Theme.SpanStyles styles) {
        FileDiff file = parsed.file();
        Vector<ParsedLine> lines = parsed.hunks().flatMap(ParsedHunk::lines).toVector();
        int digits = lines.flatMap(line ->
                        line.line().oldNumber().toList().appendAll(line.line().newNumber()))
                .max()
                .map(number -> String.valueOf(number).length())
                .getOrElse(1);
        int gutter = 2 * digits + 1 + GUTTER_END.length();
        String bar = bar(file);
        // Each line is measured once, here: its width also tells how far a changed line is padded.
        Vector<Integer> measured =
                inParallel(lines.toList(), line -> width(line.line().text())).toVector();
        int width = Math.max(
                width(bar), gutter + measured.map(text -> text + 1).max().getOrElse(0));
        Blocks blocks = new Blocks(gutter + 2, RemarkLines.blockWidth(width), style);
        Layout layout = new Layout(file.path(), index, digits, width - gutter, width, blocks, syntax, words, styles);
        Vector<LineRemarks> onLines = remarks.map(
                        placed -> placed.lines().flatMap(hunk -> hunk).toVector())
                .getOrElse(() -> Vector.fill(lines.size(), () -> NO_REMARKS));
        boolean unplaced = remarks.exists(placed -> !placed.unplaced().isEmpty());
        Vector<Row> rows = Vector.range(0, lines.size())
                .map(at ->
                        new Row(lines.get(at), measured.get(at), onLines.get(at), unplaced || at < lines.size() - 1));
        Vector<Vector<Row>> byHunk =
                cut(rows, parsed.hunks().map(hunk -> hunk.lines().size()));
        return List.<Pending>of(() -> List.of(new Marked(
                                Line.from(styled(padded(bar, width), Theme.Classes.DIFF_FILE_HEADER, styles)),
                                Option.of(new Mark(new Anchor.File(file.path()), 1, width, Option.none()))))
                        .appendAll(note(file)
                                .map(text -> Marked.plain(Line.from(styled(text, Theme.Classes.DIM, styles))))))
                .appendAll(remarks.toList()
                        .<Pending>map(placed -> () -> blocks.threads(
                                placed.onFile(), Option.none(), !file.hunks().isEmpty() || unplaced, styles)))
                .appendAll(parsed.hunks()
                        .zipWithIndex((hunk, at) -> hunk(hunk.hunk(), byHunk.get(at), at, layout))
                        .flatMap(hunk -> hunk))
                .appendAll(remarks.toList().<Pending>map(placed -> () -> blocks.unplaced(placed.unplaced(), styles)));
    }

    /**
     * What the bar of {@code file} says: what happened to it and its path, and how its mode changed if it did.
     */
    private static String bar(FileDiff file) {
        String what = Messages.get(
                switch (file.type()) {
                    case ADDED -> "content.file.added";
                    case DELETED -> "content.file.deleted";
                    case RENAMED -> "content.file.renamed";
                    case CONFLICTED -> "content.file.conflicted";
                    default -> "content.file.modified";
                });
        String path = file.origin().map(origin -> origin + " → ").getOrElse("") + file.path();
        String mode = file.mode()
                .map(change -> "  " + Messages.get("content.file.mode", change.from(), change.to()))
                .getOrElse("");
        return " " + what + "  " + path + mode + " ";
    }

    /**
     * Why {@code file} has no lines to show, if it has none and that does not go without saying.
     */
    private static Option<String> note(FileDiff file) {
        return file.binary()
                ? Option.of(Messages.get("content.file.binary"))
                : Option.when(
                        file.hunks().isEmpty() && file.type() == ChangeType.RENAMED,
                        () -> Messages.get("content.file.renamedUnchanged"));
    }

    /**
     * The lines still to be drawn of {@code hunk}, its {@code rows}, each with the threads after it, under a rule that
     * marks where it starts unless it has no header. The rule stands in for git's {@code @@ -a,b +c,d @@}, whose
     * numbers the gutter shows already. Not the section heading git gives after them: it is a line from above the
     * hunk, which reads as one of its own. A header whose numbers cannot be read, whose lines are not numbered,
     * follows the rule as git printed it. The first hunk of a file, at {@code index} 0, has no rule, as the bar of the
     * file marks where it starts, unless its header has to be shown.
     */
    private static List<Pending> hunk(Hunk hunk, Vector<Row> rows, int index, Layout layout) {
        boolean first = index == 0;
        Theme.SpanStyles styles = layout.styles();
        Span rule = styled("┄".repeat(2 * layout.digits() + 1) + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, styles);
        List<Pending> header = hunk.header().isEmpty() || (first && hunk.numbered())
                ? List.empty()
                : List.of(() -> List.of(Marked.plain(
                        hunk.numbered()
                                ? Line.from(rule)
                                : Line.from(rule, styled(hunk.header(), Theme.Classes.DIFF_HUNK, styles)))));
        return header.appendAll(rows.<Pending>map(row -> () -> {
            Option<Anchor.OnLine> anchor = Anchor.of(layout.path(), row.parsed().line());
            return layout.blocks()
                    .threads(row.remarks().threads(), anchor, row.followed(), styles)
                    .prepend(new Marked(
                            line(row, layout),
                            anchor.map(on -> new Mark(
                                    on,
                                    1,
                                    layout.fileWidth(),
                                    Option.of(new DiffStops.HunkAt(layout.file(), index))))));
        }));
    }

    /**
     * The gutter of {@code row}: the numbers of its line in the old and the new file, and where a review thread ends
     * on it, or covers it, a mark in {@link Theme.Classes#DIFF_THREAD} in place of the rule (ADR 55).
     */
    private static List<Span> gutter(Row row, Layout layout) {
        DiffLine line = row.parsed().line();
        LineRemarks remarks = row.remarks();
        Theme.SpanStyles styles = layout.styles();
        String numbers = number(line.oldNumber(), layout.digits()) + " " + number(line.newNumber(), layout.digits());
        return !remarks.threads().isEmpty() || remarks.covered()
                ? List.of(
                        styled(numbers + " ", Theme.Classes.DIFF_LINE_NUMBER, styles),
                        styled(remarks.threads().isEmpty() ? "┃" : "●", Theme.Classes.DIFF_THREAD, styles),
                        styled(" ", Theme.Classes.DIFF_LINE_NUMBER, styles))
                : List.of(styled(numbers + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, styles));
    }

    /**
     * The line of {@code row} after its gutter, with its segments, and its tokens if it was lexed and the
     * {@code layout} colours code. Changed lines are padded to the width of the layout, so their background makes a
     * block.
     */
    private static Line line(Row row, Layout layout) {
        DiffLine line = row.parsed().line();
        List<Span> gutter = gutter(row, layout);
        Option<List<Token>> tokens = layout.syntax() ? row.parsed().tokens() : Option.none();
        return switch (line.kind()) {
            case CONTEXT ->
                tokens.map(lexed -> Line.from(pieces(row.parsed().words(), lexed)
                                .map(piece -> context(piece, layout.styles()))
                                .prepend(Span.raw(" "))
                                .prependAll(gutter)
                                .toJavaList()))
                        .getOrElse(() -> Line.from(
                                gutter.append(Span.raw(" " + line.text())).toJavaList()));
            case NOTE ->
                Line.from(gutter.append(styled(line.text(), Theme.Classes.DIM, layout.styles()))
                        .toJavaList());
            case ADDED -> changed(row, gutter, tokens, Change.ADDED, layout);
            case REMOVED -> changed(row, gutter, tokens, Change.REMOVED, layout);
        };
    }

    /**
     * The changed line of {@code row}: its marker and its unchanged parts in the line class of {@code change}, its
     * changed words in its word class if the {@code layout} highlights them, padded to the width of the {@code layout}. Its code is coloured by its
     * {@code tokens} if it was lexed, unless the line class gives it no background: then its colour is what tells it
     * is changed, and a line of tokens only would look like context.
     */
    private static Line changed(Row row, List<Span> gutter, Option<List<Token>> tokens, Change change, Layout layout) {
        Theme.SpanStyles styles = layout.styles();
        Option<List<Token>> coloured =
                tokens.filter(ignored -> styles.of(change.lineClass()).bg().isPresent());
        List<Piece> pieces = pieces(row.parsed().words(), coloured.getOrElse(List.empty()));
        // Each piece is drawn as a span by itself, and pieces cut from a grapheme, like an emoji from its variation
        // selector, are narrower together than their text: only a line of one piece is as wide as it was measured.
        int used = 1
                + (pieces.size() == 1
                        ? row.measured()
                        : pieces.map(piece -> width(piece.text())).sum().intValue());
        List<Span> spans = pieces.map(piece -> {
                    String cssClass = piece.changed() && layout.words() ? change.wordClass() : change.lineClass();
                    return coloured.isDefined()
                            ? changedCode(piece, cssClass, styles)
                            : styled(piece.text(), cssClass, styles);
                })
                .prepend(styled(change.marker(), change.lineClass(), styles))
                .append(styled(" ".repeat(Math.max(0, layout.width() - used)), change.lineClass(), styles))
                .prependAll(gutter);
        return Line.from(spans.toJavaList());
    }

    /**
     * A piece of a context line: in the colour of its token, else plain.
     */
    private static Span context(Piece piece, Theme.SpanStyles styles) {
        return piece.kind()
                .map(kind -> styled(piece.text(), syntaxClass(kind), styles))
                .getOrElse(() -> Span.raw(piece.text()));
    }

    /**
     * A piece of a changed line in {@code diffClass}: with its background, its attributes, and the colour of its
     * token, or that of {@link Theme.Classes#SYNTAX_TEXT} outside a token, which is the plain colour unless the theme
     * gives it one.
     */
    private static Span changedCode(Piece piece, String diffClass, Theme.SpanStyles styles) {
        Style diff = styles.of(diffClass);
        Style text = styles.of(piece.kind().map(RichDiff::syntaxClass).getOrElse(Theme.Classes.SYNTAX_TEXT));
        return Span.styled(
                piece.text(), Option.ofOptional(text.fg()).map(diff::fg).getOrElse(() -> withoutFg(diff)));
    }

    /**
     * {@code style} without a colour of its own, so its text takes the terminal's.
     */
    private static Style withoutFg(Style style) {
        Style background = style.bg().map(Style.EMPTY::bg).orElse(Style.EMPTY);
        return List.ofAll(style.subModifiers())
                .foldLeft(
                        List.ofAll(style.addModifiers()).foldLeft(background, Style::addModifier),
                        Style::removeModifier);
    }

    /**
     * The text of a line in pieces that are each part of one of its {@code segments} and of one of its
     * {@code tokens} or none: a piece is cut where a segment or a token starts or ends. Both are in order, so the
     * segment and the token of each piece are found by going on from those of the piece before it: a minified file
     * has a line of thousands of tokens.
     */
    static List<Piece> pieces(List<Segment> segments, List<Token> tokens) {
        if (tokens.isEmpty()) {
            return segments.map(segment -> new Piece(segment.text(), segment.changed(), Option.none()));
        }
        String text = segments.map(Segment::text).mkString();
        Vector<Segment> inOrder = segments.toVector();
        Vector<Integer> ends = segments.scanLeft(
                        0, (end, segment) -> end + segment.text().length())
                .tail()
                .toVector();
        Vector<Token> lexed = tokens.toVector();
        List<Integer> cuts = ends.toList()
                .prepend(0)
                .appendAll(tokens.flatMap(token -> List.of(token.start(), token.end())))
                .filter(cut -> cut <= text.length())
                .sorted()
                .distinct();
        return cuts.zip(cuts.tail())
                .foldLeft(Tuple.of(List.<Piece>empty(), 0, 0), (done, piece) -> {
                    int segment = Iterator.iterate(done._2, i -> i + 1)
                            .find(i -> ends.get(i) > piece._1)
                            .get();
                    int token = Iterator.iterate(done._3, i -> i + 1)
                            .find(i -> i >= lexed.size() || lexed.get(i).end() > piece._1)
                            .get();
                    Option<Token.Kind> kind = Option.when(
                                    token < lexed.size() && lexed.get(token).start() <= piece._1,
                                    () -> lexed.get(token))
                            .map(Token::kind);
                    return Tuple.of(
                            done._1.prepend(new Piece(
                                    text.substring(piece._1, piece._2),
                                    inOrder.get(segment).changed(),
                                    kind)),
                            segment,
                            token);
                })
                ._1
                .reverse();
    }

    private static String syntaxClass(Token.Kind kind) {
        return switch (kind) {
            case KEYWORD -> Theme.Classes.SYNTAX_KEYWORD;
            case STRING -> Theme.Classes.SYNTAX_STRING;
            case COMMENT -> Theme.Classes.SYNTAX_COMMENT;
            case NUMBER -> Theme.Classes.SYNTAX_NUMBER;
        };
    }

    private static String number(Option<Integer> number, int digits) {
        String text = number.map(String::valueOf).getOrElse("");
        return " ".repeat(digits - text.length()) + text;
    }

    private static String padded(String text, int width) {
        return text + " ".repeat(Math.max(0, width - width(text)));
    }

    private static int width(String text) {
        return Span.raw(text).width();
    }

    private static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
        return Span.styled(text, styles.of(cssClass));
    }

    /**
     * A piece of the text of a line: whether it is part of a changed word, and the kind of token it is part of.
     */
    record Piece(String text, boolean changed, Option<Token.Kind> kind) {}

    /**
     * Lines still to be drawn: what a file is cut into once it is measured, so that its parts are drawn side by side.
     */
    @FunctionalInterface
    private interface Pending {

        List<Marked> draw();
    }

    /**
     * A line drawn, and the stop of the cursor that starts on it, if one does.
     */
    private record Marked(Line line, Option<Mark> mark) {

        static Marked plain(Line line) {
            return new Marked(line, Option.none());
        }
    }

    /**
     * A stop of the cursor (ADR 60): what it is, how many lines it has itself, after which the lines that are no stop
     * are part of it too, and how many columns the cursor is tinted on.
     *
     * @param anchor what it is
     * @param lines  how many lines it has itself
     * @param width  how many columns its tint covers: the width of its file
     * @param hunk   the hunk of a line, which a range stays in; none for what is not a line
     */
    record Mark(Anchor anchor, int lines, int width, Option<DiffStops.HunkAt> hunk) {}

    /**
     * Where a stop of the cursor starts.
     *
     * @param mark the stop
     * @param row  the line it starts on
     */
    record Stop(Mark mark, int row) {}

    /**
     * The lines of a diff, and where each stop of the cursor in them starts, in order.
     */
    record Drawn(List<Line> lines, List<Stop> stops) {

        private static Drawn of(List<Marked> marked) {
            return new Drawn(
                    marked.map(Marked::line),
                    marked.zipWithIndex().flatMap(line -> line._1().mark().map(mark -> new Stop(mark, line._2()))));
        }

        /**
         * {@code lines}, without a stop: what has no cursor.
         */
        static Drawn plain(List<Line> lines) {
            return new Drawn(lines, List.empty());
        }

        /**
         * These lines after {@code before}, their stops moved down by as many lines.
         */
        Drawn after(List<Line> before) {
            return new Drawn(
                    lines.prependAll(before), stops.map(stop -> new Stop(stop.mark(), stop.row() + before.size())));
        }

        /**
         * These lines with what comes before their first stop as a stop of its own, the lead, if anything does.
         */
        Drawn withLead() {
            return stops.headOption()
                    .filter(first -> first.row() > 0)
                    .map(first -> new Drawn(
                            lines,
                            stops.prepend(new Stop(new Mark(new Anchor.Lead(), first.row(), 0, Option.none()), 0))))
                    .getOrElse(this);
        }
    }

    /**
     * What drawing each line of a file takes from the file: how many {@code digits} its numbers have, how many
     * columns after the gutter its changed lines are padded to, its {@code width}, how the threads on its lines are
     * drawn, whether its code is coloured by its syntax, whether the changed words of a line are highlighted, and the
     * styles of the theme.
     */
    private record Layout(
            String path,
            int file,
            int digits,
            int width,
            int fileWidth,
            Blocks blocks,
            boolean syntax,
            boolean words,
            Theme.SpanStyles styles) {}

    /**
     * A line of a file with what drawing it takes besides the layout of its file: how many columns its text was
     * {@code measured} at, the review threads on it, and whether anything in its file follows them, which a blank
     * line then sets them apart from.
     */
    private record Row(ParsedLine parsed, int measured, LineRemarks remarks, boolean followed) {}

    /**
     * How a changed line is drawn: its marker, the class of the line and that of its changed words.
     */
    private record Change(String marker, String lineClass, String wordClass) {

        static final Change ADDED = new Change("+", Theme.Classes.DIFF_ADDED_LINE, Theme.Classes.DIFF_ADDED_WORD);
        static final Change REMOVED = new Change("-", Theme.Classes.DIFF_REMOVED_LINE, Theme.Classes.DIFF_REMOVED_WORD);
    }

    /**
     * How the review threads of a file are drawn: as blocks that start {@code indent} columns in, two after the
     * gutter, and are {@code width} columns wide, as {@code style} says.
     */
    private record Blocks(int indent, int width, RemarkLines.RemarkStyle style) {

        /**
         * The lines of {@code threads}, after {@code line} if they are, each a stop of the cursor, then a blank line
         * that sets them apart from what follows them in their file, if they are {@code followed} by anything.
         */
        List<Marked> threads(
                List<ReviewThread> threads, Option<Anchor.OnLine> line, boolean followed, Theme.SpanStyles styles) {
            return marked(threads, line, RemarkLines.blocks(threads, indent, width, style, styles))
                    .appendAll(followed && !threads.isEmpty() ? List.of(Marked.plain(Line.from(""))) : List.empty());
        }

        /**
         * The lines of {@code threads}, which cannot be placed, under a line that says so, each a stop of the cursor.
         */
        List<Marked> unplaced(List<ReviewThread> threads, Theme.SpanStyles styles) {
            return threads.isEmpty()
                    ? List.empty()
                    : marked(threads, Option.none(), RemarkLines.unplacedBlocks(threads, indent, width, style, styles))
                            .prepend(Marked.plain(RemarkLines.unplacedHeading(indent, styles)));
        }

        /**
         * The {@code blocks} of {@code threads}, with a blank line between two of them, the first line of each the
         * start of its stop.
         */
        private List<Marked> marked(List<ReviewThread> threads, Option<Anchor.OnLine> line, List<List<Line>> blocks) {
            return threads.zip(blocks)
                    .map(block -> block._2()
                            .zipWithIndex()
                            .map(drawn -> new Marked(
                                    drawn._1(),
                                    Option.when(
                                            drawn._2() == 0,
                                            () -> new Mark(
                                                    new Anchor.Thread(block._1().id(), line),
                                                    block._2().size(),
                                                    indent + width,
                                                    Option.none())))))
                    .intersperse(List.of(Marked.plain(Line.from(""))))
                    .flatMap(Function.identity());
        }
    }
}
