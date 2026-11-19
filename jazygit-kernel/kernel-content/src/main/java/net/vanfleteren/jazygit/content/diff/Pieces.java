package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.text.Span;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.syntax.Token;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The text of a line cut in pieces by the words that changed in it and by its tokens, and how a piece is styled.
 */
final class Pieces {

    private Pieces() {}

    /**
     * The text of a line in pieces that are each part of one of its {@code segments} and of one of its
     * {@code tokens} or none: a piece is cut where a segment or a token starts or ends. Both are in order, so the
     * segment and the token of each piece are found by going on from those of the piece before it: a minified file
     * has a line of thousands of tokens.
     */
    static List<Piece> of(List<Segment> segments, Seq<Token> tokens) {
        if (tokens.isEmpty()) {
            return segments.map(segment -> new Piece(segment.text(), segment.changed(), Option.none()));
        }
        String text = segments.map(Segment::text).mkString();
        // 0, then where each segment ends.
        List<Integer> bounds =
                segments.scanLeft(0, (end, segment) -> end + segment.text().length());
        Vector<Integer> ends = bounds.tail().toVector();
        // A token often ends where the next starts: the same cut once, before they are sorted.
        List<Integer> cuts = bounds.appendAll(tokens.flatMap(token -> List.of(token.start(), token.end())))
                .filter(cut -> cut <= text.length())
                .distinct()
                .sorted();
        Cutter cutter = new Cutter(text, segments.toVector(), ends, tokens.toVector());
        return cuts.zipWith(cuts.tail(), Cut::new)
                .foldLeft(new Cursor(List.empty(), 0, 0), cutter::next)
                .pieces()
                .reverse();
    }

    /**
     * A piece of a context line: in the colour of its token, else plain.
     */
    static Span context(Piece piece, Theme.SpanStyles styles) {
        return piece.kind()
                .map(kind -> RichDiff.styled(piece.text(), syntaxClass(kind), styles))
                .getOrElse(() -> Span.raw(piece.text()));
    }

    /**
     * A piece of a changed line in {@code diffClass}: with its background, its attributes, and the colour of its
     * token, or that of {@link Theme.Classes#SYNTAX_TEXT} outside a token, which is the plain colour unless the theme
     * gives it one.
     */
    static Span changedCode(Piece piece, String diffClass, Theme.SpanStyles styles) {
        return Span.styled(
                piece.text(),
                styles.code(diffClass, piece.kind().map(Pieces::syntaxClass).getOrElse(Theme.Classes.SYNTAX_TEXT)));
    }

    static String syntaxClass(Token.Kind kind) {
        return switch (kind) {
            case KEYWORD -> Theme.Classes.SYNTAX_KEYWORD;
            case STRING -> Theme.Classes.SYNTAX_STRING;
            case COMMENT -> Theme.Classes.SYNTAX_COMMENT;
            case NUMBER -> Theme.Classes.SYNTAX_NUMBER;
            case ANNOTATION -> Theme.Classes.SYNTAX_ANNOTATION;
        };
    }

    /**
     * A piece of the text of a line: whether it is part of a changed word, and the kind of token it is part of.
     */
    record Piece(String text, boolean changed, Option<Token.Kind> kind) {}

    /**
     * How far cutting a line in pieces got: the pieces cut, the last first, and the index of the segment and of the
     * token the last is in, or after.
     */
    private record Cursor(List<Piece> pieces, int segment, int token) {}

    /**
     * A line cut in pieces: its text, its segments, where each of those ends, and its tokens.
     */
    private record Cutter(String text, Vector<Segment> segments, Vector<Integer> ends, Vector<Token> tokens) {

        /**
         * {@code at} with the piece {@code cut} cut too: its segment and its token are found by going on from those of
         * the piece before it.
         */
        Cursor next(Cursor at, Cut cut) {
            int segment = Iterator.iterate(at.segment(), i -> i + 1)
                    .find(i -> ends.get(i) > cut.from())
                    .get();
            int token = Iterator.iterate(at.token(), i -> i + 1)
                    .find(i -> i >= tokens.size() || tokens.get(i).end() > cut.from())
                    .get();
            Option<Token.Kind> kind = Option.when(
                            token < tokens.size() && tokens.get(token).start() <= cut.from(), () -> tokens.get(token))
                    .map(Token::kind);
            Piece piece = new Piece(
                    text.substring(cut.from(), cut.to()), segments.get(segment).changed(), kind);
            return new Cursor(at.pieces().prepend(piece), segment, token);
        }
    }

    /**
     * The piece of a line from {@code from} up to, not with, {@code to}.
     */
    private record Cut(int from, int to) {}
}
