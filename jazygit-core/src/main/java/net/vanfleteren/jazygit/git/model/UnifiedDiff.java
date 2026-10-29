package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A unified diff as git prints it, parsed into its files, their hunks and their lines.
 *
 * @param preamble what comes before the first file, like the {@code --stat} of {@code git show}
 * @param files    the files, in the order of the diff
 */
public record UnifiedDiff(List<String> preamble, List<FileDiff> files) {

    private static final String FILE_START = "diff --git ";
    /**
     * How git starts a path a merge left unmerged in the changes of the working tree: as a combined diff, whose path
     * it quotes as that of {@code diff --git}.
     */
    private static final String COMBINED_START = "diff --cc ";
    /**
     * How git starts a path a merge left unmerged in the staged changes, and one deleted on one side of the merge in
     * the changes of the working tree: on one line, with its path as it is, never quoted.
     */
    private static final String UNMERGED_START = "* Unmerged path ";

    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@ ?(.*)");

    /**
     * {@code text} parsed. What it does not know is kept: before the first file in the preamble, and in a file as a
     * {@link DiffLine.Kind#NOTE} of its last hunk, or of a hunk of its own before its first one.
     */
    public static UnifiedDiff parse(String text) {
        List<String> lines = List.ofAll(text.lines());
        List<String> preamble = lines.takeWhile(line -> !startsFile(line));
        return new UnifiedDiff(preamble, files(lines.drop(preamble.size()), List.empty()));
    }

    private static List<FileDiff> files(List<String> lines, List<FileDiff> parsed) {
        if (lines.isEmpty()) {
            return parsed.reverse();
        }
        List<String> rest = lines.tail();
        List<String> own = rest.takeWhile(line -> !startsFile(line));
        String start = lines.head();
        FileDiff file = start.startsWith(FILE_START) ? file(start, own) : conflicted(start, own);
        return files(rest.drop(own.size()), parsed.prepend(file));
    }

    private static boolean startsFile(String line) {
        return line.startsWith(FILE_START) || line.startsWith(COMBINED_START) || line.startsWith(UNMERGED_START);
    }

    /**
     * The conflicted file that starts with the line {@code start}, {@code diff --cc <path>} or
     * {@code * Unmerged path <path>}, and goes on with {@code lines}. The numbers of a combined diff's hunks,
     * {@code @@@ … @@@}, cannot be read, so their lines are kept as git printed them.
     */
    private static FileDiff conflicted(String start, List<String> lines) {
        List<String> header = lines.takeWhile(line -> !line.startsWith("@@"));
        String named = start.startsWith(COMBINED_START)
                ? path(start.substring(COMBINED_START.length()))
                : start.substring(UNMERGED_START.length());
        return new FileDiff(
                ChangeType.CONFLICTED,
                side(header, "+++ b/").orElse(side(header, "--- a/")).getOrElse(named),
                Option.none(),
                Option.none(),
                header.exists(line -> line.startsWith("Binary files ")),
                fileHunks(header, lines.drop(header.size())));
    }

    /**
     * The file that starts with the line {@code start}, {@code diff --git a/<path> b/<path>}, and goes on with
     * {@code lines}: its header, then its hunks.
     */
    private static FileDiff file(String start, List<String> lines) {
        List<String> header = lines.takeWhile(line -> !line.startsWith("@@"));
        Option<String> renamedFrom = value(header, "rename from ");
        Option<String> renamedTo = value(header, "rename to ");
        Option<String> copiedTo = value(header, "copy to ");
        Option<String> oldMode = value(header, "old mode ");
        Option<String> newMode = value(header, "new mode ");
        Option<String> target = side(header, "+++ b/").orElse(side(header, "--- a/"));
        String path = renamedTo.orElse(copiedTo).orElse(target).getOrElse(() -> startPath(start));
        ChangeType type = renamedFrom.isDefined()
                ? ChangeType.RENAMED
                : header.exists(line -> line.startsWith("new file mode ")) || copiedTo.isDefined()
                        ? ChangeType.ADDED
                        : header.exists(line -> line.startsWith("deleted file mode "))
                                ? ChangeType.DELETED
                                : ChangeType.MODIFIED;
        return new FileDiff(
                type,
                path,
                renamedFrom,
                oldMode.flatMap(from -> newMode.map(to -> new ModeChange(from, to))),
                header.exists(line -> line.startsWith("Binary files ")),
                fileHunks(header, lines.drop(header.size())));
    }

    /**
     * The hunks of a file with {@code header} that go on with {@code lines}: the lines of the header it does not
     * know, as a hunk of their own, then those of {@code lines}.
     */
    private static List<Hunk> fileHunks(List<String> header, List<String> lines) {
        List<DiffLine> notes = header.filter(UnifiedDiff::unknownHeader).map(DiffLine::note);
        List<Hunk> hunks = hunks(lines, List.empty());
        return notes.isEmpty() ? hunks : hunks.prepend(new Hunk("", notes));
    }

    /**
     * Whether {@code line} of the header of a file is none of those whose meaning the file holds.
     */
    private static boolean unknownHeader(String line) {
        return List.of(
                        "index ",
                        "similarity index ",
                        "dissimilarity index ",
                        "rename from ",
                        "rename to ",
                        "copy from ",
                        "copy to ",
                        "old mode ",
                        "new mode ",
                        "new file mode ",
                        "deleted file mode ",
                        "--- ",
                        "+++ ",
                        "Binary files ")
                .forAll(known -> !line.startsWith(known));
    }

    private static Option<String> value(List<String> header, String prefix) {
        return header.find(line -> line.startsWith(prefix)).map(line -> path(line.substring(prefix.length())));
    }

    /**
     * The path of {@code ---} or {@code +++}, unless it is {@code /dev/null}; git quotes the whole of it, prefix
     * included, when it quotes, and ends a path with a space in it with a tab.
     */
    private static Option<String> side(List<String> header, String prefix) {
        String quoted = prefix.substring(0, 4) + "\"" + prefix.substring(4);
        return header.find(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .map(path -> path.endsWith("\t") ? path.substring(0, path.length() - 1) : path)
                .orElse(() -> header.find(line -> line.startsWith(quoted))
                        .map(line -> path(line.substring(4)).substring(2)));
    }

    /**
     * The path the start of a file names, {@code diff --git a/<path> b/<path>}, in which the path is the same twice,
     * as a rename or a copy says its paths on lines of its own.
     */
    private static String startPath(String start) {
        String paths = start.substring(FILE_START.length());
        if (paths.startsWith("\"")) {
            return path(paths.substring(0, closingQuote(paths) + 1)).substring(2);
        }
        int length = (paths.length() - " a/ b/".length() + 1) / 2;
        return paths.substring(2, Math.min(paths.length(), 2 + Math.max(0, length)));
    }

    private static int closingQuote(String quoted) {
        return List.range(1, quoted.length())
                .find(i -> quoted.charAt(i) == '"' && quoted.charAt(i - 1) != '\\')
                .getOrElse(quoted.length() - 1);
    }

    /**
     * {@code raw} as git writes a path: as it is, or in double quotes with C escapes, octal for each byte of a
     * character that is not plain ASCII.
     */
    static String path(String raw) {
        if (!raw.startsWith("\"") || !raw.endsWith("\"") || raw.length() < 2) {
            return raw;
        }
        byte[] bytes = unquote(raw.substring(1, raw.length() - 1)).getBytes(StandardCharsets.ISO_8859_1);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * The bytes {@code quoted}, the inside of a quoted path, stands for, one character for each byte.
     */
    private static String unquote(String quoted) {
        if (quoted.isEmpty()) {
            return "";
        }
        int c = quoted.codePointAt(0);
        if (c != '\\' || quoted.length() == 1) {
            return new String(Character.toString(c).getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)
                    + unquote(quoted.substring(Character.charCount(c)));
        } else if (quoted.length() >= 4 && quoted.substring(1, 4).chars().allMatch(d -> d >= '0' && d <= '7')) {
            return asByte(Integer.parseInt(quoted.substring(1, 4), 8)) + unquote(quoted.substring(4));
        } else {
            return asByte(escaped(quoted.charAt(1))) + unquote(quoted.substring(2));
        }
    }

    /**
     * The byte {@code value} as one character, as {@code unquote} gives the bytes; above 255 only its lowest byte
     * counts.
     */
    private static String asByte(int value) {
        return String.valueOf((char) (value & 0xFF));
    }

    private static int escaped(char c) {
        return switch (c) {
            case 'a' -> 7;
            case 'b' -> '\b';
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'v' -> 11;
            case 'f' -> '\f';
            case 'r' -> '\r';
            default -> c;
        };
    }

    private static List<Hunk> hunks(List<String> lines, List<Hunk> parsed) {
        if (lines.isEmpty()) {
            return parsed.reverse();
        }
        String header = lines.head();
        List<String> own = lines.tail().takeWhile(line -> !line.startsWith("@@"));
        Matcher numbers = HUNK.matcher(header);
        Hunk hunk = numbers.matches()
                ? new Hunk(
                        header, numbered(own, Integer.parseInt(numbers.group(1)), Integer.parseInt(numbers.group(2))))
                : new Hunk(header, own.map(DiffLine::note));
        return hunks(lines.drop(1 + own.size()), parsed.prepend(hunk));
    }

    /**
     * The lines of a hunk, numbered from line {@code oldStart} of the old file and {@code newStart} of the new one.
     */
    private static List<DiffLine> numbered(List<String> lines, int oldStart, int newStart) {
        return lines.foldLeft(new Numbering(List.empty(), oldStart, newStart), Numbering::next)
                .lines()
                .reverse();
    }

    /**
     * The lines numbered so far, newest first, and the numbers of the next line in the old and the new file.
     */
    private record Numbering(List<DiffLine> lines, int oldNumber, int newNumber) {

        Numbering next(String line) {
            char kind = line.isEmpty() ? ' ' : line.charAt(0);
            String text = line.isEmpty() ? "" : line.substring(1);
            return switch (kind) {
                case ' ' ->
                    new Numbering(
                            lines.prepend(new DiffLine(
                                    DiffLine.Kind.CONTEXT, text, Option.of(oldNumber), Option.of(newNumber))),
                            oldNumber + 1,
                            newNumber + 1);
                case '-' ->
                    new Numbering(
                            lines.prepend(
                                    new DiffLine(DiffLine.Kind.REMOVED, text, Option.of(oldNumber), Option.none())),
                            oldNumber + 1,
                            newNumber);
                case '+' ->
                    new Numbering(
                            lines.prepend(new DiffLine(DiffLine.Kind.ADDED, text, Option.none(), Option.of(newNumber))),
                            oldNumber,
                            newNumber + 1);
                default -> new Numbering(lines.prepend(DiffLine.note(line)), oldNumber, newNumber);
            };
        }
    }

    /**
     * One file of a diff.
     *
     * @param type   what happened to it: {@link ChangeType#ADDED} (also for a copy), {@link ChangeType#MODIFIED},
     *               {@link ChangeType#DELETED}, {@link ChangeType#RENAMED}, or {@link ChangeType#CONFLICTED} for a
     *               path a merge left unmerged
     * @param path   its path, the new one of a rename
     * @param origin the path a renamed file was moved from
     * @param mode   how its mode changed, if it did
     * @param binary whether it is a binary file, which has no lines to show
     * @param hunks  its hunks; empty when it has no lines to show
     */
    public record FileDiff(
            ChangeType type,
            String path,
            Option<String> origin,
            Option<ModeChange> mode,
            boolean binary,
            List<Hunk> hunks) {}

    /**
     * The mode of a file changed from {@code from} to {@code to}, like {@code 100644} to {@code 100755}.
     */
    public record ModeChange(String from, String to) {}

    /**
     * A hunk of a file.
     *
     * @param header its {@code @@ -a,b +c,d @@} line, as git printed it; empty for the lines of a file's header git
     *               printed that the diff does not know
     */
    public record Hunk(String header, List<DiffLine> lines) {

        /**
         * Whether git's numbers could be read from the header, so its lines are numbered.
         */
        public boolean numbered() {
            return HUNK.matcher(header).matches();
        }
    }

    /**
     * A line of a hunk, without its marker.
     *
     * @param oldNumber its number in the old file, unless it was added
     * @param newNumber its number in the new file, unless it was removed
     */
    public record DiffLine(Kind kind, String text, Option<Integer> oldNumber, Option<Integer> newNumber) {

        /**
         * A line that is shown as it is, unnumbered.
         */
        static DiffLine note(String line) {
            return new DiffLine(Kind.NOTE, line, Option.none(), Option.none());
        }

        public enum Kind {
            CONTEXT,
            ADDED,
            REMOVED,
            /**
             * What git says about the lines, like {@code \ No newline at end of file}, or a line the diff does not
             * know; with its marker.
             */
            NOTE
        }
    }
}
