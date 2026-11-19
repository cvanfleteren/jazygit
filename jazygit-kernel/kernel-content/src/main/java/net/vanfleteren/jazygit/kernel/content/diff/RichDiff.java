package net.vanfleteren.jazygit.kernel.content.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.git.model.LinePairs;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.state.diff.Expansion;
import net.vanfleteren.jazygit.kernel.state.diff.Filtering;
import net.vanfleteren.jazygit.kernel.state.diff.PlacedRemarks;
import net.vanfleteren.jazygit.kernel.state.diff.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.kernel.state.diff.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.kernel.state.diff.Rendered;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * A unified diff drawn rich (ADR 44): what comes before its first file as it is, then each file under a bar that
 * says what happened to it, its lines numbered in the old and the new file, and the words that changed in a changed
 * line highlighted. Each file is drawn in the same sections ({@link FileSections}), its hunks laid out as in the
 * unified view ({@link UnifiedHunk}) or side by side ({@link SplitHunk}), unless a version of it is rendered in place
 * of its diff ({@link RenderedVersion}).
 */
public final class RichDiff {

    /**
     * How wide a version is rendered while the width of its area is not known yet (ADR 103).
     */
    public static final int RENDERED_WIDTH = 80;

    /**
     * How wide an area has to be for the split view (ADR 139): each half then shows about 40 columns of code.
     */
    public static final int SPLIT_WIDTH = 100;

    // The rule between the halves of the split view.
    static final String DIVIDER = "│";
    /**
     * What sets the text of a line apart from its marker, as GitHub does: {@code + - item} reads as a list item that
     * was added, where {@code +- item} reads as neither.
     */
    static final String MARGIN = " ";

    static final LineRemarks NO_REMARKS = new LineRemarks(List.empty(), false);
    static final Pending BLANK = () -> List.of(Marked.plain(Line.from("")));

    private RichDiff() {}

    /**
     * The lines of the diff of {@code folded}, styled, drawn as {@code drawing} says. It was parsed, its words compared
     * and its code lexed when it was read (ADR 50), so drawing it only styles it. The review threads of a pull request,
     * {@code remarks}, are drawn with it if it has them (ADR 55): each after its line, which the gutter marks, one about
     * a whole file under its bar, and the ones that cannot be placed at the end of their file. It comes with where each
     * stop of the cursor starts in the lines (ADR 60): the bar of each file, each line that has a side and a number,
     * each thread, and each comment of an open thread but its first. Each file has a marker for the lines it leaves
     * out, as far as {@code folded} knows them (ADR 77). The lines of a hunk are shown in pairs where they can be (ADR
     * 83, {@link LinePairs}), but those of {@code asGiven}, the hunk a range is open in, which are shown as git gives
     * them. Side by side, it is unified, with a note that says why, when the area is narrower than
     * {@link #SPLIT_WIDTH}.
     */
    public static Drawn drawn(
            Expansion.Folded folded, Option<ShownRemarks> remarks, Option<DiffStops.HunkAt> asGiven, Drawing drawing) {
        boolean narrow = drawing.split() == Split.SIDE_BY_SIDE && drawing.width() < SPLIT_WIDTH;
        Drawn drawn = laidOut(folded, remarks, asGiven, narrow ? drawing.withSplit(Split.UNIFIED) : drawing);
        return narrow ? drawn.withNote(Messages.get("content.split.narrow", SPLIT_WIDTH)) : drawn;
    }

    /**
     * The lines of the diff of {@code folded}, as {@link #drawn} draws them, side by side if {@code drawing} says so
     * whatever the width of its area.
     */
    private static Drawn laidOut(
            Expansion.Folded folded, Option<ShownRemarks> remarks, Option<DiffStops.HunkAt> asGiven, Drawing drawing) {
        ParsedDiff diff = folded.diff();
        UnifiedDiff parsed = diff.diff();
        Vector<Option<FileRemarks>> placed = remarks.map(
                        shown -> PlacedRemarks.place(parsed, shown.threads(), shown.onLines())
                                .files()
                                .map(Option::some)
                                .toVector())
                .getOrElse(() -> Vector.fill(parsed.files().size(), Option::none));
        RemarkLines.RemarkStyle style = remarks.map(shown -> new RemarkLines.RemarkStyle(
                        shown.collapsed(), shown.sending(), shown.deleting(), shown.now(), shown.pictures()))
                .getOrElse(() -> new RemarkLines.RemarkStyle(Instant.EPOCH));
        // Each file is drawn on a thread of its own, so that a diff of many files holds up the render thread for less;
        // the lines of a long one are measured on threads of their own too (FileSections).
        List<List<Marked>> files = inParallel(
                diff.files().zipWithIndex((file, index) -> new FileAt(file, placed.get(index), style, index)),
                file -> file(
                                file,
                                folded.fold(file.parsed().file().path()),
                                folded.hiding(file.parsed().file().path()),
                                asGiven.filter(hunk -> hunk.file() == file.index())
                                        .map(DiffStops.HunkAt::hunk),
                                drawing)
                        .flatMap(Pending::draw));
        Drawn drawn = Marked.drawn(parsed.preamble()
                .<Marked>map(line -> Marked.of(() -> Line.from(Span.raw(line)), Option.none()))
                .appendAll(files.intersperse(BLANK.draw()).flatMap(lines -> lines)));
        // How many hunks the diff filters hide, or that they are switched off where they would hide some, which the
        // title says: drawn with the diff, so it is worked out once for it, not for each frame (ADR 144).
        Drawn filtered = folded.filtered() == 0
                ? drawn
                : drawn.withNote(
                        folded.filtersOff()
                                ? Messages.get("content.filtered.off")
                                : Messages.get("content.filtered.hidden", folded.filtered()));
        // The column of a version blamed shortened to fit its area, which the title says (ADR 147).
        Drawn noted = diff.files()
                .flatMap(file -> Gutter.blameColumn(
                                file.hunks().flatMap(ParsedHunk::lines).toVector(),
                                folded.fold(file.file().path()),
                                drawing.width(),
                                drawing.styles())
                        .flatMap(BlameColumn::note))
                .headOption()
                .map(filtered::withNote)
                .getOrElse(filtered);
        return drawing.split() == Split.SIDE_BY_SIDE ? noted.withDivider((drawing.width() - 1) / 2) : noted;
    }

    /**
     * What {@code draw} gives for each of {@code items}, in their order, each worked out on a thread of the common
     * pool. Drawing is pure, and the render thread waits for it, so nothing changes the theme while it runs.
     */
    static <T, R> List<R> inParallel(List<T> items, Function<T, R> draw) {
        // Collected, not read through an iterator as List.ofAll and Stream.ofAll read a java.util.stream.Stream: that
        // runs a parallel stream on the calling thread only.
        return items.toJavaParallelStream().map(draw).collect(List.collector());
    }

    /**
     * The lines still to be drawn of the file {@code fileAt}, with the review threads on it, folded as {@code fold} says,
     * its hunks hidden as {@code hiding} says (ADR 144), drawn as {@code drawing} says: a version of it rendered (ADR
     * 103), or its sections ({@link FileSections}), its hunks laid out side by side if {@code drawing} says so (ADR
     * 139), but for a version of it or a conflicted file, else as in the unified view, with the lines of its hunk
     * {@code asGiven} as git gives them.
     */
    private static List<Pending> file(
            FileAt fileAt, Expansion.Fold fold, Filtering.Hiding hiding, Option<Integer> asGiven, Drawing drawing) {
        ParsedFile parsed = fileAt.parsed();
        Option<FileVersion> version = fold.version();
        if (fold.rendered()
                && version.isDefined()
                && Rendered.of(parsed.file().path()).isDefined()) {
            return List.of(() -> RenderedVersion.of(parsed, version.get(), fold, drawing.width(), drawing.styles()));
        }
        boolean split = drawing.split() == Split.SIDE_BY_SIDE
                && version.isEmpty()
                && parsed.hunks().forAll(hunk -> hunk.hunk().numbered());
        return FileSections.of(
                fileAt, fold, hiding, drawing, split ? SplitHunk::of : lines -> UnifiedHunk.of(lines, asGiven));
    }

    /**
     * The anchor of {@code line} on the left side, by its number in the old file, if it is a context line: the split
     * view has a stop for it there (ADR 139).
     */
    static Option<Anchor> leftOfContext(String path, DiffLine line) {
        return Option.when(line.kind() == DiffLine.Kind.CONTEXT, line::oldNumber)
                .flatMap(number -> number)
                .map(number -> new Anchor.OnLine(path, ReviewThread.Side.LEFT, number));
    }

    static int width(String text) {
        return Span.raw(text).width();
    }

    static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
        return Span.styled(text, styles.of(cssClass));
    }

    /**
     * Lines still to be drawn: what a file is cut into once it is measured, so that its parts are drawn side by side.
     */
    @FunctionalInterface
    interface Pending {

        List<Marked> draw();
    }

    /**
     * How a changed line is drawn: its marker and the class of the marker, the class of the line and that of its
     * changed words.
     */
    record Change(String marker, String markerClass, String lineClass, String wordClass) {

        static final Change ADDED =
                new Change("+", Theme.Classes.DIFF_ADDED, Theme.Classes.DIFF_ADDED_LINE, Theme.Classes.DIFF_ADDED_WORD);
        static final Change REMOVED = new Change(
                "-", Theme.Classes.DIFF_REMOVED, Theme.Classes.DIFF_REMOVED_LINE, Theme.Classes.DIFF_REMOVED_WORD);
    }

    /**
     * A file of a diff to draw: {@code parsed}, the review threads placed on it, if they are shown, how they are
     * drawn, {@code style}, and its {@code index} in the diff.
     */
    record FileAt(ParsedFile parsed, Option<FileRemarks> remarks, RemarkLines.RemarkStyle style, int index) {}

    /**
     * What a diff drawn highlights (ADR 53): its code coloured by its {@link #SYNTAX}, and the {@link #WORDS} that
     * changed in a changed line, else the whole line in its line class.
     */
    public enum Highlight {
        SYNTAX,
        WORDS
    }

    /**
     * How the lines of a diff are laid out: {@link #UNIFIED}, as git gives them, or with the old file and the new one
     * {@link #SIDE_BY_SIDE} (ADR 139).
     */
    public enum Split {
        UNIFIED,
        SIDE_BY_SIDE
    }

    /**
     * How a diff is drawn: what it {@code highlights}, in the {@code styles} of the theme, in an area {@code width}
     * columns wide, which a version of a file is rendered as wide as, its gutter included (ADR 103), and laid out as
     * {@code split} says.
     */
    public record Drawing(Set<Highlight> highlights, Theme.SpanStyles styles, int width, Split split) {

        /**
         * A diff drawn in {@code styles}, its syntax and its words highlighted, unified, {@link #RENDERED_WIDTH}
         * columns wide.
         */
        public static Drawing of(Theme.SpanStyles styles) {
            return new Drawing(HashSet.of(Highlight.values()), styles, RENDERED_WIDTH, Split.UNIFIED);
        }

        public Drawing withHighlights(Set<Highlight> highlights) {
            return new Drawing(highlights, styles, width, split);
        }

        public Drawing withWidth(int width) {
            return new Drawing(highlights, styles, width, split);
        }

        public Drawing withSplit(Split split) {
            return new Drawing(highlights, styles, width, split);
        }

        boolean has(Highlight highlight) {
            return highlights.contains(highlight);
        }
    }
}
