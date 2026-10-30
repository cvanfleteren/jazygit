package net.vanfleteren.jazygit.ui;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.git.model.syntax.Token;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * A unified diff drawn rich (ADR 44): what comes before its first file as it is, then each file under a bar that
 * says what happened to it, its lines numbered in the old and the new file, and the words that changed in a changed
 * line highlighted.
 */
final class RichDiff {

    private static final String GUTTER_END = " │ ";

    private RichDiff() {}

    /**
     * The lines of {@code diff}, styled, its code coloured by its syntax if {@code syntax} (ADR 53). It was parsed,
     * its words compared and its code lexed when it was read (ADR 50), so drawing it only styles it.
     */
    static List<Line> lines(ParsedDiff diff, boolean syntax, Theme.SpanStyles styles) {
        UnifiedDiff parsed = diff.diff();
        Vector<Marks> marks = diff.words()
                .zip(diff.tokens())
                .map(hunk -> new Marks(hunk._1, syntax ? hunk._2 : hunk._2.map(line -> List.<Token>empty())))
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
                        .flatMap(file -> file(file._1._1, file._1._2, styles)
                                .prependAll(file._2 > 0 ? List.of(Line.from("")) : List.empty())));
    }

    /**
     * The lines of {@code file}, whose hunks have the segments and the tokens {@code marks}. A file none of whose
     * lines has a token, as its language is not known, is drawn as it was before its code was coloured.
     */
    private static List<Line> file(FileDiff file, Vector<Marks> marks, Theme.SpanStyles styles) {
        boolean coloured = marks.exists(hunk -> hunk.tokens().exists(line -> !line.isEmpty()));
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
        return List.of(Line.from(styled(padded(bar, width), Theme.Classes.DIFF_FILE_HEADER, styles)))
                .appendAll(note(file).map(text -> Line.from(styled(text, Theme.Classes.DIM, styles))))
                .appendAll(file.hunks()
                        .zip(marks)
                        .zipWithIndex()
                        .flatMap(hunk ->
                                hunk(hunk._1._1, hunk._1._2, hunk._2 == 0, digits, width - gutter, coloured, styles)));
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
     * be shown. Its code is {@code coloured} by its syntax, or drawn as before.
     */
    private static List<Line> hunk(
            Hunk hunk, Marks marks, boolean first, int digits, int width, boolean coloured, Theme.SpanStyles styles) {
        Span rule = styled("┄".repeat(2 * digits + 1) + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, styles);
        List<Line> header = hunk.header().isEmpty() || (first && hunk.numbered())
                ? List.empty()
                : List.of(
                        hunk.numbered()
                                ? Line.from(rule)
                                : Line.from(rule, styled(hunk.header(), Theme.Classes.DIFF_HUNK, styles)));
        return header.appendAll(hunk.lines()
                .zip(marks.words().zip(marks.tokens()))
                .map(line ->
                        line(line._1, pieces(line._2._1, line._2._2), digits, width, new Painter(coloured, styles))));
    }

    private static Line line(DiffLine line, List<Piece> pieces, int digits, int width, Painter painter) {
        Span gutter = styled(
                number(line.oldNumber(), digits) + " " + number(line.newNumber(), digits) + GUTTER_END,
                Theme.Classes.DIFF_LINE_NUMBER,
                painter.styles());
        return switch (line.kind()) {
            case CONTEXT ->
                painter.coloured()
                        ? Line.from(pieces.map(painter::context)
                                .prepend(Span.raw(" "))
                                .prepend(gutter)
                                .toJavaList())
                        : Line.from(gutter, Span.raw(" " + line.text()));
            case NOTE -> Line.from(gutter, styled(line.text(), Theme.Classes.DIM, painter.styles()));
            case ADDED ->
                changed(
                        gutter,
                        "+",
                        pieces,
                        width,
                        Theme.Classes.DIFF_ADDED_LINE,
                        Theme.Classes.DIFF_ADDED_WORD,
                        painter);
            case REMOVED ->
                changed(
                        gutter,
                        "-",
                        pieces,
                        width,
                        Theme.Classes.DIFF_REMOVED_LINE,
                        Theme.Classes.DIFF_REMOVED_WORD,
                        painter);
        };
    }

    /**
     * A changed line: its marker and its unchanged parts in {@code lineClass}, its changed words in
     * {@code wordClass}, padded to {@code width}.
     */
    private static Line changed(
            Span gutter,
            String marker,
            List<Piece> pieces,
            int width,
            String lineClass,
            String wordClass,
            Painter painter) {
        int used = 1 + pieces.map(piece -> width(piece.text())).sum().intValue();
        List<Span> spans = pieces.map(piece -> painter.changed(piece, piece.changed() ? wordClass : lineClass))
                .prepend(styled(marker, lineClass, painter.styles()))
                .append(styled(" ".repeat(Math.max(0, width - used)), lineClass, painter.styles()))
                .prepend(gutter);
        return Line.from(spans.toJavaList());
    }

    /**
     * The text of a line in pieces that are each part of one of its {@code segments} and of one of its
     * {@code tokens} or none: a piece is cut where a segment or a token starts or ends.
     */
    static List<Piece> pieces(List<Segment> segments, List<Token> tokens) {
        if (tokens.isEmpty()) {
            return segments.map(segment -> new Piece(segment.text(), segment.changed(), Option.none()));
        }
        String text = segments.map(Segment::text).mkString();
        List<Integer> ends = segments.scanLeft(
                        0, (end, segment) -> end + segment.text().length())
                .tail();
        List<Integer> cuts = ends.prepend(0)
                .appendAll(tokens.flatMap(token -> List.of(token.start(), token.end())))
                .filter(cut -> cut <= text.length())
                .distinct()
                .sorted();
        return cuts.zip(cuts.tail())
                .map(piece -> new Piece(
                        text.substring(piece._1, piece._2),
                        segments.get(ends.indexWhere(end -> end > piece._1)).changed(),
                        tokens.find(token -> token.start() <= piece._1 && piece._1 < token.end())
                                .map(Token::kind)));
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
     * The segments and the tokens of the lines of a hunk.
     */
    private record Marks(List<List<Segment>> words, List<List<Token>> tokens) {}

    /**
     * How the pieces of a line are styled: their code {@code coloured} by its syntax (ADR 53), or as before.
     */
    private record Painter(boolean coloured, Theme.SpanStyles styles) {

        /**
         * A piece of a context line: in the colour of its token, else plain.
         */
        Span context(Piece piece) {
            return piece.kind()
                    .map(kind -> styled(piece.text(), syntaxClass(kind), styles))
                    .getOrElse(() -> Span.raw(piece.text()));
        }

        /**
         * A piece of a changed line in {@code diffClass}: with its background and the colour of its token, or the
         * plain colour outside a token. When the class gives the line no background, its colour is what tells it is
         * changed, so it is kept outside a token.
         */
        Span changed(Piece piece, String diffClass) {
            Style diff = styles.of(diffClass);
            if (!coloured || (piece.kind().isEmpty() && diff.bg().isEmpty())) {
                return Span.styled(piece.text(), diff);
            }
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
    }
}
