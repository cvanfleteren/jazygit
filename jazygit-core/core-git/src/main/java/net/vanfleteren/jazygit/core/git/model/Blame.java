package net.vanfleteren.jazygit.core.git.model;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who last changed each line of a file, as {@code git blame --porcelain} says (ADR 147).
 *
 * @param lines the lines of the file blamed, in order: line {@code n} is the {@code n - 1}th
 */
public record Blame(Vector<Line> lines) {

    /**
     * How many shades of age the commits a file is blamed on are split in.
     */
    public static final int SHADES = 5;

    /**
     * The commit git blames the lines not committed yet on: a sha of zeros.
     */
    public static final Commit NOT_COMMITTED =
            new Commit(new Sha("0".repeat(40)), "Not Committed Yet", Instant.EPOCH, "Version of the file", false);

    private static final Pattern HEADER = Pattern.compile("^([0-9a-f]{40}|[0-9a-f]{64}) (\\d+) (\\d+)(?: \\d+)?$");

    /**
     * {@code count} lines of the file at {@code path}, none of them committed yet: a file of a branch without commits,
     * which has no {@code HEAD} to blame it against (ADR 147).
     */
    public static Blame uncommitted(int count, String path) {
        return new Blame(
                Vector.rangeClosed(1, count).map(number -> new Line(NOT_COMMITTED, number, path, Option.none())));
    }

    /**
     * The line numbered {@code number}, from 1; none past the last line.
     */
    public Option<Line> at(int number) {
        return Option.when(number >= 1 && number <= lines.size(), () -> lines.get(number - 1));
    }

    /**
     * Whether the line numbered {@code number} starts a run of lines from one commit: it is the first line, or the
     * line before it is of another commit. None past the last line does.
     */
    public boolean startsRun(int number) {
        return at(number)
                .exists(line -> at(number - 1)
                        .forAll(before ->
                                !before.commit().sha().equals(line.commit().sha())));
    }

    /**
     * The shade of age of each commit the file is blamed on, from 0, the newest, to {@link #SHADES} - 1, the oldest:
     * the commits newest first, by when they were written, split in {@link #SHADES} groups as evenly as they go, by
     * rank, so that a young project shades as well as an old one. Fewer commits than shades are spread over them, the
     * newest always 0. A line not committed yet has no shade.
     */
    public Map<Sha, Integer> shades() {
        List<Commit> commits = lines.map(Line::commit)
                .filter(Commit::committed)
                .distinctBy(Commit::sha)
                .sorted(Comparator.comparing(Commit::authored)
                        .reversed()
                        .thenComparing(commit -> commit.sha().value()))
                .toList();
        int count = commits.size();
        return HashMap.ofEntries(
                commits.zipWithIndex().map(ranked -> Tuple.of(ranked._1.sha(), ranked._2 * SHADES / count)));
    }

    /**
     * The blame {@code text}, as {@code git blame --porcelain} prints it, says: for each group of lines a header,
     * {@code <sha> <original line> <final line> [<lines in the group>]}, the details of its commit the first time it
     * is named, and then the line itself after a tab. A commit named again names its path again, with what was before
     * it, only when the lines blamed on it are of more than one path; otherwise the path it was first named with holds.
     * None when it is not that, or a line is missing.
     */
    public static Option<Blame> parse(String text) {
        List<String> rows = List.of(text.split("\n", -1)).filter(row -> !row.isEmpty());
        // Folded rather than recursive, as a file can have more lines than the stack is deep.
        return rows.foldLeft(Option.of(Reading.START), (reading, row) -> reading.flatMap(at -> at.next(row)))
                .filter(reading -> reading.header().isEmpty())
                .map(reading -> reading.lines().sortBy(Tuple2::_1))
                .filter(sorted -> sorted.zipWithIndex().forAll(at -> at._1._1 == at._2 + 1))
                .map(sorted -> new Blame(sorted.map(Tuple2::_2)));
    }

    /**
     * A line of the file, blamed on {@code commit}.
     *
     * @param commit   the commit that last changed it
     * @param original its number in the file of {@code commit}
     * @param path     the path of the file in {@code commit}, which is another once the file was renamed since
     * @param previous the commit before {@code commit} that changed the file, and its path there; none when
     *                 {@code commit} added it
     */
    public record Line(Commit commit, int original, String path, Option<Previous> previous) {}

    /**
     * A commit lines are blamed on.
     *
     * @param sha      the commit; all zeros for lines not committed yet
     * @param author   the name of its author
     * @param authored when its author wrote it
     * @param summary  the first line of its message
     * @param boundary whether it is the oldest commit blame went back to, which holds the lines it did not see change
     */
    public record Commit(Sha sha, String author, Instant authored, String summary, boolean boundary) {

        /**
         * Whether it is a commit, rather than the lines not committed yet, which git blames on a sha of zeros.
         */
        public boolean committed() {
            return !sha.value().chars().allMatch(c -> c == '0');
        }
    }

    /**
     * The commit before a line's commit that changed its file, and the path of the file in it.
     */
    public record Previous(Sha sha, String path) {}

    /**
     * Where a parse of the porcelain is: the lines read, by their final number, the commits named, the path and what
     * was before of each, and the header of the line whose details are being read, with the details read of it.
     */
    private record Reading(
            Vector<Tuple2<Integer, Line>> lines,
            Map<Sha, Commit> commits,
            Map<Sha, Tuple2<String, Option<Previous>>> origins,
            Option<Header> header,
            Map<String, String> details,
            boolean boundary) {

        static final Reading START =
                new Reading(Vector.empty(), HashMap.empty(), HashMap.empty(), Option.none(), HashMap.empty(), false);

        Option<Reading> next(String row) {
            if (header.isEmpty()) {
                return Header.of(row)
                        .map(read -> new Reading(lines, commits, origins, Option.of(read), HashMap.empty(), false));
            } else if (row.startsWith("\t")) {
                return line(header.get());
            } else if (row.equals("boundary")) {
                return Option.of(new Reading(lines, commits, origins, header, details, true));
            } else {
                int space = row.indexOf(' ');
                return Option.of(
                        space < 0
                                ? this
                                : new Reading(
                                        lines,
                                        commits,
                                        origins,
                                        header,
                                        details.put(row.substring(0, space), row.substring(space + 1)),
                                        boundary));
            }
        }

        /**
         * The line {@code at} heads, with the details read since; none when its commit is named for the first time
         * without its details, or a detail cannot be read.
         */
        private Option<Reading> line(Header at) {
            Option<Commit> commit = commits.get(at.sha()).orElse(this::commit);
            Option<Tuple2<String, Option<Previous>>> origin = details.get("filename")
                    .map(UnifiedDiff::path)
                    .map(path -> Tuple.of(path, details.get("previous").flatMap(Reading::previous)))
                    .orElse(() -> origins.get(at.sha()));
            return commit.flatMap(named -> origin.map(from -> new Reading(
                    lines.append(Tuple.of(at.number(), new Line(named, at.original(), from._1, from._2))),
                    commits.put(named.sha(), named),
                    origins.put(named.sha(), from),
                    Option.none(),
                    HashMap.empty(),
                    false)));
        }

        private Option<Commit> commit() {
            return header.flatMap(at -> details.get("author")
                    .flatMap(author -> details.get("author-time")
                            .flatMap(Reading::seconds)
                            .map(authored -> new Commit(
                                    at.sha(),
                                    author,
                                    authored,
                                    details.get("summary").getOrElse(""),
                                    boundary))));
        }

        private static Option<Instant> seconds(String value) {
            return Option.of(value)
                    .filter(digits -> digits.matches("-?\\d{1,18}"))
                    .map(digits -> Instant.ofEpochSecond(Long.parseLong(digits)));
        }

        /**
         * {@code <sha> <path>}, the path quoted as git quotes paths when it must.
         */
        private static Option<Previous> previous(String value) {
            int space = value.indexOf(' ');
            return space < 0
                    ? Option.none()
                    : Sha.parse(value.substring(0, space))
                            .map(sha -> new Previous(sha, UnifiedDiff.path(value.substring(space + 1))));
        }
    }

    /**
     * The header of a group of lines: the commit, the number of its first line in the file of the commit, and in the
     * file blamed.
     */
    private record Header(Sha sha, int original, int number) {

        static Option<Header> of(String row) {
            Matcher matcher = HEADER.matcher(row);
            return Option.when(matcher.matches(), () -> matcher)
                    .flatMap(matched -> Option.when(
                                    matched.group(2).length() <= 9
                                            && matched.group(3).length() <= 9,
                                    () -> matched)
                            .map(fits -> new Header(
                                    new Sha(fits.group(1)),
                                    Integer.parseInt(fits.group(2)),
                                    Integer.parseInt(fits.group(3)))));
        }
    }
}
