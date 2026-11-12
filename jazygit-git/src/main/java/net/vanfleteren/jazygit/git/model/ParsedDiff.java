package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.Objects;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.git.model.syntax.DiffLexer;
import net.vanfleteren.jazygit.git.model.syntax.Highlighter;
import net.vanfleteren.jazygit.syntax.Token;

/**
 * A unified diff parsed, with the words of its changed lines compared and its code lexed: what the rich view draws
 * (ADR 50, 53). It is made where the diff is read, as comparing the words of a long diff takes too long to do where
 * it is drawn. It is one tree of files, hunks and lines, each line with its words and its tokens (ADR 56). A record's
 * constructor is as public as the record: it is made with {@link #of} and {@link #withNote}, which keep each part of
 * the tree as what it wraps has it.
 *
 * @param preamble what comes before the first file, as {@link UnifiedDiff#preamble} has it
 * @param files    its files, in order
 */
public record ParsedDiff(List<String> preamble, List<ParsedFile> files) {

    private static final Highlighter HIGHLIGHTER = new DiffLexer();

    /**
     * {@code text} parsed, its words compared and its code lexed. The words of all its hunks share one budget
     * ({@link WordDiff#segments}), so they are compared at once, in order, and then given to their files.
     */
    public static ParsedDiff of(String text) {
        UnifiedDiff diff = UnifiedDiff.parse(text);
        List<List<List<Segment>>> words =
                WordDiff.segments(diff.files().flatMap(FileDiff::hunks).map(Hunk::lines));
        List<List<List<List<Segment>>>> byFile = diff.files()
                .foldLeft(
                        Tuple.of(List.<List<List<List<Segment>>>>empty(), words),
                        (done, file) -> Tuple.of(
                                done._1.prepend(done._2.take(file.hunks().size())),
                                done._2.drop(file.hunks().size())))
                ._1
                .reverse();
        return new ParsedDiff(diff.preamble(), diff.files().zipWith(byFile, ParsedDiff::file));
    }

    /**
     * {@code file}, whose hunks have the segments {@code words}, with its code lexed.
     */
    private static ParsedFile file(FileDiff file, List<List<List<Segment>>> words) {
        return new ParsedFile(
                file,
                file.hunks()
                        .zipWith(
                                words.zip(HIGHLIGHTER.tokens(file)),
                                (hunk, marks) -> new ParsedHunk(
                                        hunk,
                                        hunk.lines()
                                                .zipWith(
                                                        marks._1.zip(marks._2),
                                                        (line, mark) -> ParsedLine.of(line, mark._1, mark._2)))));
    }

    /**
     * The diff as it was parsed, without its words and its tokens.
     */
    public UnifiedDiff diff() {
        return new UnifiedDiff(preamble, files.map(ParsedFile::file));
    }

    /**
     * The diff with the line {@code note} after it, as it would be parsed were the note the last line of its text:
     * in the preamble when it has no files, else a note of the last hunk of its last file, or of a hunk of its own
     * when that file has none. Its words are not compared again, as a note ends no block, and it is not lexed.
     */
    public ParsedDiff withNote(String note) {
        if (files.isEmpty()) {
            return new ParsedDiff(preamble.append(note), files);
        }
        ParsedFile last = files.last();
        ParsedLine noted = ParsedLine.of(DiffLine.note(note), List.empty(), Option.none());
        List<ParsedHunk> hunks = last.hunks().isEmpty()
                ? List.of(new ParsedHunk(new Hunk("", List.of(noted.line())), List.of(noted)))
                : last.hunks().init().append(last.hunks().last().with(noted));
        FileDiff file = last.file();
        return new ParsedDiff(
                preamble,
                files.init()
                        .append(new ParsedFile(
                                new FileDiff(
                                        file.type(),
                                        file.path(),
                                        file.origin(),
                                        file.mode(),
                                        file.binary(),
                                        hunks.map(ParsedHunk::hunk)),
                                hunks)));
    }

    /**
     * A file of the diff, and its hunks, in the order {@code file} has them.
     */
    public record ParsedFile(FileDiff file, List<ParsedHunk> hunks) {}

    /**
     * A hunk of a file, and its lines, in the order {@code hunk} has them.
     */
    public record ParsedHunk(Hunk hunk, List<ParsedLine> lines) {

        /**
         * The hunk with {@code line} after its last line.
         */
        ParsedHunk with(ParsedLine line) {
            return new ParsedHunk(new Hunk(hunk.header(), hunk.lines().append(line.line())), lines.append(line));
        }
    }

    /**
     * A line of a hunk, with its segments as {@link WordDiff#segments} has them, and its tokens as the
     * {@link Highlighter} found them, or none when it was not lexed. It is made with {@link #of} only. A class
     * rather than a record, so that what it keeps stays its own: a record's constructor and accessors are as public
     * as the record, and would give away the sentinels and the packed tokens below. It is the one model that keeps
     * arrays, which it never changes nor hands out but to its own tests: the architecture rules make an exception
     * for it by name ({@code Values.PACKED} in jazygit-test-support).
     *
     * <p>Most lines of a diff have no changed words: their one segment, the whole line unchanged, is not kept but
     * made when {@link #words} is asked for, which is only for the lines that are drawn. A long diff has tens of
     * thousands of lines, and that segment and the list that holds it took more than a tenth of what it was parsed
     * into.
     *
     * <p>Its tokens are kept the same way: each packed in an {@code int} ({@link #packed}), as an array of
     * {@link Token}s took an object for the array, one for the list of its elements and one for each token, and an
     * option around them. {@link #tokens} makes them again, only for the lines that are drawn. A line that was not
     * lexed keeps the sentinel {@link #NOT_LEXED}; one that was lexed and has no tokens, the one empty array
     * {@link #NO_TOKENS}, which is drawn otherwise.
     */
    public static final class ParsedLine {

        /**
         * The tokens of a line that was not lexed, told apart from {@link #NO_TOKENS} by identity.
         */
        static final int[] NOT_LEXED = new int[0];

        /**
         * The tokens of a lexed line without any, which all such lines share.
         */
        static final int[] NO_TOKENS = new int[0];

        // Where a token starts and how long it is take 15 bits each: a line longer than Lexer.MAX_LINE is not lexed.
        private static final int BITS = 15;
        private static final int MOST = (1 << BITS) - 1;
        private static final int KIND_BITS = 2;
        private static final Array<Token.Kind> KINDS = Array.of(Token.Kind.values());

        private final DiffLine line;
        // Its segments when its words were compared with another line's and they are not the whole line unchanged;
        // else none.
        private final List<Segment> compared;
        // Its tokens, each packed in an int, or the sentinel NOT_LEXED when it was not lexed.
        private final int[] lexed;

        private ParsedLine(DiffLine line, List<Segment> compared, int[] lexed) {
            this.line = line;
            this.compared = compared;
            this.lexed = lexed;
        }

        /**
         * {@code line} with its segments {@code words}, which are not kept when they are the whole line unchanged,
         * and its {@code tokens}, none when it was not lexed.
         */
        public static ParsedLine of(DiffLine line, List<Segment> words, Option<? extends Seq<Token>> tokens) {
            return new ParsedLine(
                    line,
                    whole(line, words) ? List.empty() : words,
                    tokens.map(ParsedLine::packed).getOrElse(NOT_LEXED));
        }

        /**
         * The line of the diff, as it was parsed.
         */
        public DiffLine line() {
            return line;
        }

        /**
         * Its segments as they are kept: none for the whole line, unchanged.
         */
        List<Segment> compared() {
            return compared;
        }

        /**
         * Its tokens as they are kept: packed, or {@link #NOT_LEXED}.
         */
        int[] lexed() {
            return lexed;
        }

        /**
         * Its segments: the whole line, unchanged, when its words were not compared or none of them changed.
         */
        public List<Segment> words() {
            return compared.isEmpty() ? List.of(new Segment(line.text(), false)) : compared;
        }

        /**
         * Its tokens, as the {@link Highlighter} found them; none when it was not lexed.
         */
        public Option<Array<Token>> tokens() {
            return Option.when(
                    lexed != NOT_LEXED,
                    () -> Array.ofAll(Arrays.stream(lexed).boxed()).map(ParsedLine::unpacked));
        }

        /**
         * {@code tokens}, each in an {@code int}: where it starts, how long it is and its kind.
         */
        static int[] packed(Seq<Token> tokens) {
            return tokens.isEmpty()
                    ? NO_TOKENS
                    : tokens.toJavaStream().mapToInt(ParsedLine::packed).toArray();
        }

        private static int packed(Token token) {
            if (token.start() > MOST || token.length() > MOST) {
                throw new IllegalArgumentException("A token past column " + MOST + " cannot be packed: " + token);
            }
            return (token.start() << (BITS + KIND_BITS))
                    | (token.length() << KIND_BITS)
                    | token.kind().ordinal();
        }

        private static Token unpacked(int packed) {
            return new Token(
                    packed >>> (BITS + KIND_BITS),
                    (packed >>> KIND_BITS) & MOST,
                    KINDS.get(packed & ((1 << KIND_BITS) - 1)));
        }

        private static boolean whole(DiffLine line, List<Segment> words) {
            return words.size() == 1
                    && !words.head().changed()
                    && words.head().text().equals(line.text());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ParsedLine that
                    && line.equals(that.line)
                    && compared.equals(that.compared)
                    && (lexed == NOT_LEXED) == (that.lexed == NOT_LEXED)
                    && Arrays.equals(lexed, that.lexed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(line, compared, lexed == NOT_LEXED, Arrays.hashCode(lexed));
        }

        @Override
        public String toString() {
            return "ParsedLine[line=" + line + ", compared=" + compared + ", tokens=" + tokens() + "]";
        }
    }
}
