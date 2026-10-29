package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;

/**
 * A unified diff parsed, with the words of its changed lines compared: what the rich view draws (ADR 49). It is made
 * where the diff is read, as comparing the words of a long diff takes too long to do where it is drawn.
 *
 * @param diff  the diff parsed
 * @param words the segments of the lines of each hunk of its files, in order, as {@link WordDiff#segments} has them
 */
public record ParsedDiff(UnifiedDiff diff, List<List<List<Segment>>> words) {

    /**
     * {@code text} parsed, and its words compared.
     */
    public static ParsedDiff of(String text) {
        UnifiedDiff diff = UnifiedDiff.parse(text);
        return new ParsedDiff(
                diff, WordDiff.segments(diff.files().flatMap(FileDiff::hunks).map(Hunk::lines)));
    }

    /**
     * The diff with the line {@code note} after it, as it would be parsed were the note the last line of its text:
     * in the preamble when it has no files, else a note of the last hunk of its last file, or of a hunk of its own
     * when that file has none. Its words are not compared again, as a note ends no block.
     */
    public ParsedDiff withNote(String note) {
        if (diff.files().isEmpty()) {
            return new ParsedDiff(new UnifiedDiff(diff.preamble().append(note), diff.files()), words);
        }
        FileDiff last = diff.files().last();
        List<Segment> whole = List.of(new Segment(note, false));
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
                        : words.init().append(words.last().append(whole)));
    }
}
