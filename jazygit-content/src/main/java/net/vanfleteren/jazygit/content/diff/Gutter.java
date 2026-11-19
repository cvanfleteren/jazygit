package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.style.Style;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The gutter of a line of a diff: the numbers of the line, how many digits they take, and the mark of a review thread
 * on it, in the unified view, the split view and a version of a file shown in place of its diff.
 */
final class Gutter {

    /**
     * What ends the gutter, before the marker of a line.
     */
    static final String END = " │ ";

    private Gutter() {}

    /**
     * How many digits each column of numbers of the gutter of {@code lines}, a file folded as {@code fold} says, takes:
     * as many as its widest number. A version keeps the gutter of the diff it is shown in place of, as wide, so its
     * code does not move sideways (ADR 102): its numbers, of the whole file, may be longer than the diff's, but fit in
     * both of its columns.
     */
    static int digits(Vector<ParsedLine> lines, Expansion.Fold fold) {
        return fold.version()
                .map(shown -> shownBlamed(fold)
                        ? ownDigits(lines, shown)
                        : Math.max(fold.digits(), ownDigits(lines, shown) / 2))
                .getOrElse(() -> lines.flatMap(line -> line.line()
                                .oldNumber()
                                .toList()
                                .appendAll(line.line().newNumber()))
                        .max()
                        .map(number -> String.valueOf(number).length())
                        .getOrElse(1));
    }

    /**
     * How many digits the widest number of {@code lines} in {@code version} has.
     */
    private static int ownDigits(Vector<ParsedLine> lines, FileVersion version) {
        return lines.flatMap(line -> version.number(line.line()))
                .max()
                .map(number -> String.valueOf(number).length())
                .getOrElse(1);
    }

    /**
     * Whether {@code fold} is of a version shown blamed (ADR 147). Its code moves sideways by the column of its blame
     * anyway, so it does not keep the gutter of the diff: its numbers are in one column as wide as its own widest.
     */
    static boolean shownBlamed(Expansion.Fold fold) {
        return fold.version().isDefined() && fold.blame().isDefined();
    }

    /**
     * How many columns the numbers of the gutter of {@code lines}, a file folded as {@code fold} says, take, with its
     * end: two columns of {@link #digits} digits and a space between them, or one for a version shown blamed.
     */
    static int numbersWidth(Vector<ParsedLine> lines, Expansion.Fold fold) {
        int digits = digits(lines, fold);
        return (shownBlamed(fold) ? digits : 2 * digits + 1) + END.length();
    }

    /**
     * The column of the blame of {@code lines}, a file folded as {@code fold} says, in an area {@code area} columns
     * wide: if it is a version shown blamed, as source (ADR 147).
     */
    static Option<BlameColumn> blameColumn(
            Vector<ParsedLine> lines, Expansion.Fold fold, int area, Theme.SpanStyles styles) {
        return fold.version().flatMap(ignored -> BlameColumn.of(fold.blame(), area, numbersWidth(lines, fold), styles));
    }

    /**
     * The gutter of a line of {@code kind} with {@code numbers}, styled for an added or a removed line when it is one,
     * and where a review thread {@code ends} on it, or covers it, a mark in {@link Theme.Classes#DIFF_THREAD} in place
     * of the rule (ADR 55).
     */
    static List<Span> spans(
            String numbers, DiffLine.Kind kind, boolean ends, boolean covered, Theme.SpanStyles styles) {
        Style numbered = switch (kind) {
            case ADDED -> styles.refined(Theme.Classes.DIFF_LINE_NUMBER, Theme.Classes.DIFF_ADDED_LINE_NUMBER);
            case REMOVED -> styles.refined(Theme.Classes.DIFF_LINE_NUMBER, Theme.Classes.DIFF_REMOVED_LINE_NUMBER);
            case CONTEXT, NOTE -> styles.of(Theme.Classes.DIFF_LINE_NUMBER);
        };
        return ends || covered
                ? List.of(
                        Span.styled(numbers + " ", numbered),
                        RichDiff.styled(ends ? "●" : "┃", Theme.Classes.DIFF_THREAD, styles),
                        Span.styled(" ", numbered))
                : List.of(Span.styled(numbers + END, numbered));
    }

    /**
     * The rule in a gutter whose numbers take {@code columns}, which marks where a hunk starts.
     */
    static Span rule(int columns, Theme.SpanStyles styles) {
        return RichDiff.styled("┄".repeat(columns) + END, Theme.Classes.DIFF_LINE_NUMBER, styles);
    }

    /**
     * The number of {@code line} in {@code version}, in the column of the diff's gutter it is in there, the other one
     * blank (ADR 102): the new version's ends where the gutter does, the original's where its first column does,
     * running on into the blank one when it is longer.
     */
    static String versionNumber(FileVersion version, DiffLine line, int digits) {
        String text = version.number(line).map(String::valueOf).getOrElse("");
        int width = 2 * digits + 1;
        return version == FileVersion.NEW
                ? " ".repeat(Math.max(0, width - text.length())) + text
                : " ".repeat(Math.max(0, digits - text.length()))
                        + text
                        + " ".repeat(Math.max(0, width - Math.max(digits, text.length())));
    }

    static String number(Option<Integer> number, int digits) {
        String text = number.map(String::valueOf).getOrElse("");
        return " ".repeat(digits - text.length()) + text;
    }
}
