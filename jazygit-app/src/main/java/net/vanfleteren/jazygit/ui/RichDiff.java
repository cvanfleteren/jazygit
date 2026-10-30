package net.vanfleteren.jazygit.ui;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.i18n.Messages;
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
        UnifiedDiff parsed = diff.diff();
        Vector<Option<FileRemarks>> placed = remarks.map(shown -> PlacedRemarks.place(parsed, shown.threads())
                        .files()
                        .map(Option::some)
                        .toVector())
                .getOrElse(() -> Vector.fill(parsed.files().size(), Option::none));
        RemarkLines.RemarkStyle style = remarks.map(shown -> new RemarkLines.RemarkStyle(shown.opened(), shown.now()))
                .getOrElse(() -> new RemarkLines.RemarkStyle(false, Instant.EPOCH));
        Vector<Marks> marks = diff.words()
                .zip(diff.tokens())
                .map(hunk -> new Marks(hunk._1, syntax ? hunk._2 : hunk._2.map(line -> Option.none())))
                .toVector();
        List<Tuple2<FileDiff, Vector<Marks>>> files = parsed.files()
                .foldLeft(
                        Tuple.of(List.<Tuple2<FileDiff, Vector<Marks>>>empty(), 0),
                        (done, file) -> Tuple.of(
                                done._1.prepend(Tuple.of(
                                        file,
                                        marks.slice(
                                                done._2, done._2 + file.hunks().size()))),
                                done._2 + file.hunks().size()))
                ._1
                .reverse();
        return parsed.preamble()
                .map(line -> Line.from(Span.raw(line)))
                .appendAll(files.zipWithIndex()
                        .flatMap(file -> file(file._1._1, file._1._2, placed.get(file._2), style, styles)
                                .prependAll(file._2 > 0 ? List.of(Line.from("")) : List.empty())));
    }

    /**
     * The lines of {@code file}, whose hunks have the segments and the tokens {@code marks}, with the review threads
     * {@code remarks} on it, drawn as {@code style} says.
     */
    private static List<Line> file(
            FileDiff file,
            Vector<Marks> marks,
            Option<FileRemarks> remarks,
            RemarkLines.RemarkStyle style,
            Theme.SpanStyles styles) {
        int digits = file.hunks()
                .flatMap(Hunk::lines)
                .flatMap(line -> line.oldNumber().toList().appendAll(line.newNumber()))
                .max()
                .map(number -> String.valueOf(number).length())
                .getOrElse(1);
        int gutter = 2 * digits + 1 + GUTTER_END.length();
        String bar = bar(file);
        int width = Math.max(
                width(bar),
                gutter
                        + file.hunks()
                                .flatMap(Hunk::lines)
                                .map(line -> width(line.text()) + 1)
                                .max()
                                .getOrElse(0));
        Blocks blocks = new Blocks(gutter + 2, RemarkLines.blockWidth(width), style);
        Vector<List<LineRemarks>> onLines = remarks.map(FileRemarks::lines)
                .getOrElse(() -> file.hunks().map(hunk -> hunk.lines().map(line -> NO_REMARKS)))
                .toVector();
        return List.of(Line.from(styled(padded(bar, width), Theme.Classes.DIFF_FILE_HEADER, styles)))
                .appendAll(note(file).map(text -> Line.from(styled(text, Theme.Classes.DIM, styles))))
                .appendAll(remarks.toList().flatMap(placed -> blocks.threads(placed.onFile(), styles)))
                .appendAll(file.hunks()
                        .zip(marks)
                        .zipWithIndex()
                        .flatMap(hunk -> hunk(
                                hunk._1._1,
                                hunk._1._2,
                                onLines.get(hunk._2),
                                blocks,
                                hunk._2 == 0,
                                digits,
                                width - gutter,
                                styles)))
                .appendAll(remarks.toList()
                        .flatMap(placed -> RemarkLines.unplaced(
                                placed.unplaced(), blocks.indent(), blocks.width(), style, styles)));
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
     * The lines of {@code hunk}, whose lines have the segments and the tokens {@code marks}, under a rule that marks
     * where it starts unless it has no header: changed lines are padded to {@code width}, so their background makes a
     * block. The rule stands in for git's {@code @@ -a,b +c,d @@}, whose numbers the gutter shows already. Not the
     * section heading git gives after them: it is a line from above the hunk, which reads as one of its own. A header
     * whose numbers cannot be read, whose lines are not numbered, follows the rule as git printed it. The
     * {@code first} hunk of a file has no rule, as the bar of the file marks where it starts, unless its header has to
     * be shown. A line that was lexed is coloured by its syntax, one that was not is drawn as before.
     */
    private static List<Line> hunk(
            Hunk hunk,
            Marks marks,
            List<LineRemarks> remarks,
            Blocks blocks,
            boolean first,
            int digits,
            int width,
            Theme.SpanStyles styles) {
        Span rule = styled("┄".repeat(2 * digits + 1) + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, styles);
        List<Line> header = hunk.header().isEmpty() || (first && hunk.numbered())
                ? List.empty()
                : List.of(
                        hunk.numbered()
                                ? Line.from(rule)
                                : Line.from(rule, styled(hunk.header(), Theme.Classes.DIFF_HUNK, styles)));
        return header.appendAll(hunk.lines()
                .zip(marks.words().zip(marks.tokens()))
                .zip(remarks)
                .flatMap(line -> blocks.threads(line._2.threads(), styles)
                        .prepend(line(
                                line._1._1,
                                line._1._2._1,
                                line._1._2._2,
                                gutter(line._1._1, line._2, digits, styles),
                                width,
                                styles))));
    }

    /**
     * The gutter of {@code line}: its numbers in the old and the new file, and where a review thread ends on it, or
     * covers it, a mark in {@link Theme.Classes#DIFF_THREAD} in place of the rule (ADR 55).
     */
    private static List<Span> gutter(DiffLine line, LineRemarks remarks, int digits, Theme.SpanStyles styles) {
        String numbers = number(line.oldNumber(), digits) + " " + number(line.newNumber(), digits);
        return !remarks.threads().isEmpty() || remarks.covered()
                ? List.of(
                        styled(numbers + " ", Theme.Classes.DIFF_LINE_NUMBER, styles),
                        styled(remarks.threads().isEmpty() ? "┃" : "●", Theme.Classes.DIFF_THREAD, styles),
                        styled(" ", Theme.Classes.DIFF_LINE_NUMBER, styles))
                : List.of(styled(numbers + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, styles));
    }

    /**
     * {@code line}, with its {@code segments}, and its {@code tokens} if its hunk was lexed, after its {@code gutter}.
     */
    private static Line line(
            DiffLine line,
            List<Segment> segments,
            Option<List<Token>> tokens,
            List<Span> gutter,
            int width,
            Theme.SpanStyles styles) {
        return switch (line.kind()) {
            case CONTEXT ->
                tokens.map(lexed -> Line.from(pieces(segments, lexed)
                                .map(piece -> context(piece, styles))
                                .prepend(Span.raw(" "))
                                .prependAll(gutter)
                                .toJavaList()))
                        .getOrElse(() -> Line.from(
                                gutter.append(Span.raw(" " + line.text())).toJavaList()));
            case NOTE ->
                Line.from(gutter.append(styled(line.text(), Theme.Classes.DIM, styles))
                        .toJavaList());
            case ADDED ->
                changed(
                        gutter,
                        "+",
                        segments,
                        tokens,
                        width,
                        Theme.Classes.DIFF_ADDED_LINE,
                        Theme.Classes.DIFF_ADDED_WORD,
                        styles);
            case REMOVED ->
                changed(
                        gutter,
                        "-",
                        segments,
                        tokens,
                        width,
                        Theme.Classes.DIFF_REMOVED_LINE,
                        Theme.Classes.DIFF_REMOVED_WORD,
                        styles);
        };
    }

    /**
     * A changed line: its marker and its unchanged parts in {@code lineClass}, its changed words in
     * {@code wordClass}, padded to {@code width}. Its code is coloured by its {@code tokens} if it was lexed, unless
     * {@code lineClass} gives it no background: then its colour is what tells it is changed, and a line of tokens only
     * would look like context.
     */
    private static Line changed(
            List<Span> gutter,
            String marker,
            List<Segment> segments,
            Option<List<Token>> tokens,
            int width,
            String lineClass,
            String wordClass,
            Theme.SpanStyles styles) {
        Option<List<Token>> coloured =
                tokens.filter(ignored -> styles.of(lineClass).bg().isPresent());
        List<Piece> pieces = pieces(segments, coloured.getOrElse(List.empty()));
        int used = 1 + pieces.map(piece -> width(piece.text())).sum().intValue();
        List<Span> spans = pieces.map(piece -> coloured.isDefined()
                        ? changedCode(piece, piece.changed() ? wordClass : lineClass, styles)
                        : styled(piece.text(), piece.changed() ? wordClass : lineClass, styles))
                .prepend(styled(marker, lineClass, styles))
                .append(styled(" ".repeat(Math.max(0, width - used)), lineClass, styles))
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
     * The segments of the lines of a hunk, and the tokens of each that was lexed.
     */
    private record Marks(List<List<Segment>> words, List<Option<List<Token>>> tokens) {}

    /**
     * How the review threads of a file are drawn: as blocks that start {@code indent} columns in, two after the
     * gutter, and are {@code width} columns wide, as {@code style} says.
     */
    private record Blocks(int indent, int width, RemarkLines.RemarkStyle style) {

        List<Line> threads(List<ReviewThread> threads, Theme.SpanStyles styles) {
            return RemarkLines.threads(threads, indent, width, style, styles);
        }
    }
}
