package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.Hunk;

/**
 * Which hunks of a diff its filters hide (ADR 144). A hunk is hidden when each of its added and removed lines matches
 * an enabled filter that applies to its file. A blank line, or one of only whitespace, counts as matching when
 * another of its lines matched one, as organizing imports adds and removes the blank lines between their groups; a
 * hunk of blank lines only shows.
 */
public final class DiffFiltering {

    private static final Pattern HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");

    private DiffFiltering() {}

    /**
     * Of {@code filters}, those that apply to the file at {@code path}.
     */
    public static List<DiffFilter> applying(List<DiffFilter> filters, String path) {
        return filters.filter(filter -> filter.appliesTo(path));
    }

    /**
     * The filters that hide {@code hunk}, of those {@code applying} to its file, if they hide it: those that matched
     * one of its lines, in their order. None when a line matches none, or the hunk changes nothing.
     */
    public static Option<List<DiffFilter>> hiding(Hunk hunk, List<DiffFilter> applying) {
        List<String> changed = hunk.lines()
                .filter(line -> line.kind() == DiffLine.Kind.ADDED || line.kind() == DiffLine.Kind.REMOVED)
                .map(DiffLine::text);
        if (applying.isEmpty() || changed.isEmpty()) {
            return Option.none();
        }
        List<String> unmatched = changed.filter(text -> !applying.exists(filter -> filter.matches(text)));
        boolean matchedAny = unmatched.size() < changed.size();
        boolean hidden = matchedAny && unmatched.forAll(String::isBlank);
        return Option.when(
                hidden,
                () -> applying.filter(filter -> changed.exists(text -> !text.isBlank() && filter.matches(text))
                        || changed.forAll(text -> filter.matches(text))));
    }

    /**
     * Where {@code hunk} starts in the old file and in the new one, as its header says, which tells it apart from the
     * other hunks of its file also when it only removes lines; none when its header cannot be read.
     */
    public static Option<HunkStart> start(Hunk hunk) {
        Matcher numbers = HEADER.matcher(hunk.header());
        return Option.when(
                numbers.matches(),
                () -> new HunkStart(Integer.parseInt(numbers.group(1)), Integer.parseInt(numbers.group(2))));
    }

    /**
     * Where a hunk starts: the line {@code oldLine} of the old file and {@code newLine} of the new one, as its header
     * says.
     */
    public record HunkStart(int oldLine, int newLine) {}
}
