package net.vanfleteren.jazygit.core.git.model;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.core.git.model.DiffFiltering.HunkStart;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.Hunk;

/**
 * The hunks of a file its filters hide, judged on the hunks git gave, and the hunks it is drawn in cut where they start
 * and end (ADR 144). A file read whole and folded (ADR 77) is drawn in other hunks than git's: a hidden hunk's lines
 * fall in one of them, which is cut around them, so they are hidden there too and expanding a gap never shows them.
 */
public final class HiddenHunks {

    private static final Pattern HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");

    private HiddenHunks() {}

    /**
     * The hunks of {@code file}, as git gave them, that the filters {@code applying} to it hide, but those {@code kept}
     * shows: opened, or with a review thread on one of its lines.
     */
    public static List<Hidden> of(ParsedFile file, List<DiffFilter> applying, Predicate<Hunk> kept) {
        return file.hunks()
                .map(ParsedHunk::hunk)
                .filter(hunk -> !kept.test(hunk))
                .flatMap(hunk -> DiffFiltering.hiding(hunk, applying)
                        .flatMap(filters -> hidden(hunk, filters.map(DiffFilter::name))));
    }

    /**
     * The hunks {@code drawn} is drawn in, each cut into pieces: the runs of its lines that are of a hunk of
     * {@code hidden}, and those between them. A hunk none of whose lines are hidden is one piece, as it was.
     */
    public static List<Piece> split(ParsedFile drawn, List<Hidden> hidden) {
        return drawn.hunks().flatMap(hunk -> pieces(hunk, hidden));
    }

    private static List<Piece> pieces(ParsedHunk hunk, List<Hidden> hidden) {
        Option<Ranges> ranges = ranges(hunk.hunk());
        List<Option<Hidden>> owners = hunk.lines().map(line -> hidden.find(of -> of.has(line.line())));
        if (ranges.isEmpty() || owners.forAll(Option::isEmpty)) {
            return List.of(new Piece(hunk, Option.none()));
        }
        if (owners.forAll(owner -> owner.equals(owners.head()))) {
            return List.of(new Piece(hunk, owners.head()));
        }
        // A note, like "\ No newline at end of file", goes with the line before it.
        List<Option<Hidden>> owned = owners.zip(hunk.lines())
                .foldLeft(Tuple.of(List.<Option<Hidden>>empty(), Option.<Hidden>none()), (done, owner) -> {
                    Option<Hidden> own = owner._2().line().kind() == DiffLine.Kind.NOTE ? done._2 : owner._1();
                    return Tuple.of(done._1.prepend(own), own);
                })
                ._1
                .reverse();
        List<Run> runs = runs(owned.zip(hunk.lines()));
        // The lines before each run, in no order: a piece only counts them.
        List<List<ParsedLine>> befores =
                runs.scanLeft(List.<ParsedLine>empty(), (before, run) -> before.prependAll(run.lines()));
        return runs.zipWith(
                befores, (run, before) -> new Piece(piece(run.lines(), ranges.get(), before), run.hidden()));
    }

    /**
     * {@code lines} cut where who they are of changes, in order.
     */
    private static List<Run> runs(List<Tuple2<Option<Hidden>, ParsedLine>> lines) {
        return lines.foldLeft(
                        List.<Run>empty(),
                        (done, line) -> done.headOption()
                                .filter(last -> last.hidden().equals(line._1()))
                                .map(last -> done.tail()
                                        .prepend(new Run(
                                                last.hidden(), last.lines().prepend(line._2()))))
                                .getOrElse(() -> done.prepend(new Run(line._1(), List.of(line._2())))))
                .map(run -> new Run(run.hidden(), run.lines().reverse()))
                .reverse();
    }

    /**
     * The hunk of {@code own}, the lines of a hunk of {@code ranges} after {@code before}, under the header git would
     * give it.
     */
    private static ParsedHunk piece(List<ParsedLine> own, Ranges ranges, List<ParsedLine> before) {
        int oldBefore = before.count(line -> line.line().oldNumber().isDefined());
        int newBefore = before.count(line -> line.line().newNumber().isDefined());
        int oldLength = own.count(line -> line.line().oldNumber().isDefined());
        int newLength = own.count(line -> line.line().newNumber().isDefined());
        int oldBase = ranges.oldLength() > 0 ? ranges.oldStart() - 1 : ranges.oldStart();
        int newBase = ranges.newLength() > 0 ? ranges.newStart() - 1 : ranges.newStart();
        String header = "@@ -" + (oldBase + oldBefore + (oldLength > 0 ? 1 : 0)) + "," + oldLength + " +"
                + (newBase + newBefore + (newLength > 0 ? 1 : 0)) + "," + newLength + " @@";
        return new ParsedHunk(new Hunk(header, own.map(ParsedLine::line)), own);
    }

    private static Option<Hidden> hidden(Hunk hunk, List<String> filters) {
        return ranges(hunk)
                .map(ranges -> new Hidden(
                        new HunkStart(ranges.oldStart(), ranges.newStart()),
                        ranges,
                        filters,
                        hunk.lines()
                                .count(line ->
                                        line.kind() == DiffLine.Kind.ADDED || line.kind() == DiffLine.Kind.REMOVED)));
    }

    private static Option<Ranges> ranges(Hunk hunk) {
        Matcher numbers = HEADER.matcher(hunk.header());
        return Option.when(
                numbers.matches(),
                () -> new Ranges(
                        Integer.parseInt(numbers.group(1)),
                        numbers.group(2) == null ? 1 : Integer.parseInt(numbers.group(2)),
                        Integer.parseInt(numbers.group(3)),
                        numbers.group(4) == null ? 1 : Integer.parseInt(numbers.group(4))));
    }

    /**
     * A hunk of git's that filters hide.
     *
     * @param start   where it starts in the old file and the new one, which tells it apart
     * @param ranges  the lines it has of each
     * @param filters the names of the filters that hide it, in their order
     * @param changes how many lines it adds or removes
     */
    public record Hidden(HunkStart start, Ranges ranges, List<String> filters, int changes) {

        /**
         * Whether {@code line} is one of its lines, by its number in the old file or the new one.
         */
        public boolean has(DiffLine line) {
            return line.oldNumber().exists(number -> ranges.has(true, number))
                    || line.newNumber().exists(number -> ranges.has(false, number));
        }
    }

    /**
     * Where a hunk starts in the old file and the new one, and how many lines it has in each, as its header says.
     */
    public record Ranges(int oldStart, int oldLength, int newStart, int newLength) {

        /**
         * Where the hunk starts in the old file and the new one.
         */
        public HunkStart start() {
            return new HunkStart(oldStart, newStart);
        }

        /**
         * Whether the line {@code number} of the old file, if {@code old}, or of the new one is one of the hunk's.
         */
        public boolean has(boolean old, int number) {
            return old
                    ? number >= oldStart && number < oldStart + oldLength
                    : number >= newStart && number < newStart + newLength;
        }
    }

    /**
     * A piece of a hunk as it is drawn, and the hunk of git's that hides it, if one does.
     */
    public record Piece(ParsedHunk hunk, Option<Hidden> hidden) {}

    /**
     * Lines of a hunk next to each other that are of the same hunk of git's that hides them, or of none.
     */
    private record Run(Option<Hidden> hidden, List<ParsedLine> lines) {}
}
