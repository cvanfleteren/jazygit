package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Covered;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;

/**
 * The lines a file of a diff leaves out between its hunks, and a file read with the whole file as context folded to
 * the hunks git would give, with the lines the user expanded besides (ADR 76).
 */
public final class Folding {

    private Folding() {}

    /**
     * The lines of the new file from {@code from} to {@code to}, both in.
     */
    public record Lines(int from, int to) {

        boolean has(int number) {
            return number >= from && number <= to;
        }
    }

    /**
     * Lines a file leaves out: before its hunk {@code before}, or after its last one when that is as many as it has,
     * the lines of the new file from {@code from} up to {@code until}, which is not one of them; none when it is not
     * known how many follow the last hunk.
     */
    public record Gap(int before, int from, Option<Integer> until) {

        /**
         * How many lines it leaves out, when that is known.
         */
        public Option<Integer> hidden() {
            return until.map(end -> end - from);
        }
    }

    /**
     * The lines {@code file} leaves out, in order, the new file being {@code length} lines long when that is known. A
     * file added or deleted leaves none out, nor does one whose hunks cannot be read.
     */
    public static List<Gap> gaps(FileDiff file, Option<Integer> length) {
        List<Option<Covered>> covered = file.hunks().map(Hunk::covered);
        if (file.binary()
                || file.type() == ChangeType.ADDED
                || file.type() == ChangeType.DELETED
                || covered.isEmpty()
                || covered.exists(Option::isEmpty)) {
            return List.empty();
        }
        Vector<Covered> hunks = covered.map(Option::get).toVector();
        List<Gap> between = List.range(0, hunks.size())
                .map(at -> new Gap(at, at == 0 ? 1 : hunks.get(at - 1).until(), Option.of(hunks.get(at).from())));
        int after = hunks.last().until();
        Gap last = new Gap(hunks.size(), after, length.map(lines -> Math.max(after, lines + 1)));
        return between.append(last).filter(gap -> gap.hidden().forAll(hidden -> hidden > 0));
    }

    /**
     * How many lines the new file of {@code whole}, read with the whole file as context, has.
     */
    public static int length(ParsedFile whole) {
        return whole.hunks()
                .flatMap(ParsedHunk::lines)
                .count(line -> line.line().newNumber().isDefined());
    }

    /**
     * Whether {@code whole}, read with the whole file as context, has the changes {@code shown} has: else it was read
     * before or after the file changed.
     */
    public static boolean agrees(ParsedFile whole, ParsedFile shown) {
        return changes(whole).equals(changes(shown));
    }

    private static List<DiffLine> changes(ParsedFile file) {
        return file.hunks()
                .flatMap(ParsedHunk::lines)
                .map(ParsedLine::line)
                .filter(line -> line.kind() == DiffLine.Kind.ADDED || line.kind() == DiffLine.Kind.REMOVED);
    }

    /**
     * {@code whole}, read with the whole file as context, folded to its changes with {@code context} lines around
     * them, and the {@code expanded} lines of the new file: each run of lines shown is a hunk, whose header says where
     * it is as git's would.
     */
    public static ParsedFile fold(ParsedFile whole, int context, List<Lines> expanded) {
        Vector<ParsedLine> lines = whole.hunks().flatMap(ParsedHunk::lines).toVector();
        Vector<Integer> fromChange = distances(lines);
        Vector<Integer> toChange = distances(lines.reverse()).reverse();
        Vector<Boolean> own = Vector.range(0, lines.size())
                .map(at -> shown(lines.get(at).line(), Math.min(fromChange.get(at), toChange.get(at)), context, expanded));
        // A note, as that a file has no newline at its end, goes with the line before it.
        Vector<Boolean> shown = Vector.range(0, lines.size())
                .map(at -> lines.get(at).line().kind() == DiffLine.Kind.NOTE ? at == 0 || own.get(at - 1) : own.get(at));
        List<ParsedHunk> hunks = runs(shown).map(run -> hunk(lines, run));
        FileDiff file = whole.file();
        return new ParsedFile(
                new FileDiff(
                        file.type(),
                        file.path(),
                        file.origin(),
                        file.mode(),
                        file.binary(),
                        hunks.map(ParsedHunk::hunk)),
                hunks);
    }

    /**
     * Whether {@code line}, {@code distance} lines from a change, is shown: a change is; a line of context within
     * {@code context} lines of a change, or {@code expanded}. A note is left to the line before it.
     */
    private static boolean shown(DiffLine line, int distance, int context, List<Lines> expanded) {
        return switch (line.kind()) {
            case ADDED, REMOVED -> true;
            case NOTE -> false;
            case CONTEXT ->
                distance <= context
                        || line.newNumber().exists(number -> expanded.exists(asked -> asked.has(number)));
        };
    }

    /**
     * How many lines each of {@code lines} is after the last change before it, or on it; a large number when there is
     * none.
     */
    private static Vector<Integer> distances(Vector<ParsedLine> lines) {
        return lines.scanLeft(Integer.MAX_VALUE / 2, (since, line) -> changed(line.line()) ? 0 : since + 1)
                .tail();
    }

    private static boolean changed(DiffLine line) {
        return line.kind() == DiffLine.Kind.ADDED || line.kind() == DiffLine.Kind.REMOVED;
    }

    /**
     * The runs of lines that are {@code shown}, each from its first index to its last, both in.
     */
    private static List<Lines> runs(Vector<Boolean> shown) {
        return List.range(0, shown.size())
                .filter(shown::get)
                .foldLeft(
                        List.<Lines>empty(),
                        (runs, at) -> runs.headOption()
                                .filter(run -> run.to() == at - 1)
                                .map(run -> runs.tail().prepend(new Lines(run.from(), at)))
                                .getOrElse(() -> runs.prepend(new Lines(at, at))))
                .reverse();
    }

    /**
     * The hunk of the lines of {@code run}, under the header git would give it: where it starts in the old and the new
     * file, and how many lines it has in each.
     */
    private static ParsedHunk hunk(Vector<ParsedLine> lines, Lines run) {
        List<ParsedLine> own = lines.slice(run.from(), run.to() + 1).toList();
        Vector<ParsedLine> before = lines.slice(0, run.from());
        int oldBefore = before.count(line -> line.line().oldNumber().isDefined());
        int newBefore = before.count(line -> line.line().newNumber().isDefined());
        int oldLength = own.count(line -> line.line().oldNumber().isDefined());
        int newLength = own.count(line -> line.line().newNumber().isDefined());
        String header = "@@ -" + (oldLength > 0 ? oldBefore + 1 : oldBefore) + "," + oldLength + " +"
                + (newLength > 0 ? newBefore + 1 : newBefore) + "," + newLength + " @@";
        return new ParsedHunk(new Hunk(header, own.map(ParsedLine::line)), own);
    }
}
