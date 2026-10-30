package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
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
                                                        (line, mark) -> new ParsedLine(line, mark._1, mark._2)))));
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
        ParsedLine noted = new ParsedLine(DiffLine.note(note), List.of(new Segment(note, false)), Option.none());
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
     * {@link Highlighter} found them, or none when it was not lexed.
     */
    public record ParsedLine(DiffLine line, List<Segment> words, Option<List<Token>> tokens) {}
}
