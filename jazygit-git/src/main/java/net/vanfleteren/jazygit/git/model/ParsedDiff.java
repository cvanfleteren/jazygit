package net.vanfleteren.jazygit.git.model;

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
 * it is drawn.
 *
 * @param diff   the diff parsed
 * @param words  the segments of the lines of each hunk of its files, in order, as {@link WordDiff#segments} has them
 * @param tokens the tokens of the lines of each hunk of its files, in the shape of {@code words}, as the
 *               {@link Highlighter} found them; none for a line that was not lexed
 */
public record ParsedDiff(UnifiedDiff diff, List<List<List<Segment>>> words, List<List<Option<List<Token>>>> tokens) {

    private static final Highlighter HIGHLIGHTER = new DiffLexer();

    /**
     * {@code text} parsed, its words compared and its code lexed.
     */
    public static ParsedDiff of(String text) {
        UnifiedDiff diff = UnifiedDiff.parse(text);
        return new ParsedDiff(
                diff,
                WordDiff.segments(diff.files().flatMap(FileDiff::hunks).map(Hunk::lines)),
                diff.files().flatMap(HIGHLIGHTER::tokens));
    }

    /**
     * The diff with the line {@code note} after it, as it would be parsed were the note the last line of its text:
     * in the preamble when it has no files, else a note of the last hunk of its last file, or of a hunk of its own
     * when that file has none. Its words are not compared again, as a note ends no block, and it is not lexed.
     */
    public ParsedDiff withNote(String note) {
        if (diff.files().isEmpty()) {
            return new ParsedDiff(new UnifiedDiff(diff.preamble().append(note), diff.files()), words, tokens);
        }
        FileDiff last = diff.files().last();
        List<Segment> whole = List.of(new Segment(note, false));
        Option<List<Token>> unlexed = Option.none();
        FileDiff noted = new FileDiff(
                last.type(),
                last.path(),
                last.origin(),
                last.mode(),
                last.binary(),
                last.hunks().isEmpty()
                        ? List.of(new Hunk("", List.of(DiffLine.note(note))))
                        : last.hunks()
                                .init()
                                .append(new Hunk(
                                        last.hunks().last().header(),
                                        last.hunks().last().lines().append(DiffLine.note(note)))));
        return new ParsedDiff(
                new UnifiedDiff(diff.preamble(), diff.files().init().append(noted)),
                last.hunks().isEmpty()
                        ? words.append(List.of(whole))
                        : words.init().append(words.last().append(whole)),
                last.hunks().isEmpty()
                        ? tokens.append(List.of(unlexed))
                        : tokens.init().append(tokens.last().append(unlexed)));
    }
}
