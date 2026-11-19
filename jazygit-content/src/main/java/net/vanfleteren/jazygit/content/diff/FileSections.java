package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.vanfleteren.jazygit.content.diff.RichDiff.Drawing;
import net.vanfleteren.jazygit.content.diff.RichDiff.FileAt;
import net.vanfleteren.jazygit.content.diff.RichDiff.Pending;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.git.model.HiddenHunks;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Filtering;
import net.vanfleteren.jazygit.state.diff.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.diff.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.ui.content.Drawn.Mark;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The sections a file of a diff is drawn in, in the unified view and the split view alike: its bar, why it has no
 * lines, the threads on it, its hunks with the markers of the lines it leaves out and of the hunks filters hide, the
 * marker after its last hunk, and the threads that cannot be placed. Only how its hunks are laid out differs
 * ({@link HunkLayout}).
 */
final class FileSections {

    private FileSections() {}

    /**
     * The lines still to be drawn of the file {@code fileAt}, with the review threads on it, folded as {@code fold}
     * says, its hunks hidden as {@code hiding} says (ADR 144), drawn as {@code drawing} says, its hunks laid out as the
     * layout {@code laid} makes of its lines says: its bar, the threads on it, its hunks once the width of its lines is
     * known, and the threads that cannot be placed. A version of it shown in place of its diff (ADR 102) leaves no line
     * out, and the threads on the other version's lines are not shown with it.
     */
    static List<Pending> of(
            FileAt fileAt,
            Expansion.Fold fold,
            Filtering.Hiding hiding,
            Drawing drawing,
            Function<Lines, HunkLayout> laid) {
        ParsedFile parsed = fileAt.parsed();
        FileDiff file = parsed.file();
        Option<FileRemarks> remarks = fileAt.remarks();
        Option<FileVersion> version = fold.version();
        Theme.SpanStyles styles = drawing.styles();
        Vector<ParsedLine> lines = parsed.hunks().flatMap(ParsedHunk::lines).toVector();
        // Each line is measured once, here: its width also tells how far a changed line is padded. On the threads of
        // the common pool, as measuring is what drawing a long file spends most on: a diff of one long file draws in
        // half the time this way (RichDiffBenchmark).
        Vector<Integer> measured = RichDiff.inParallel(
                        lines.toList(), line -> RichDiff.width(line.line().text()))
                .toVector();
        Vector<LineRemarks> onLines = remarks.map(
                        placed -> placed.lines().flatMap(hunk -> hunk).toVector())
                .getOrElse(() -> Vector.fill(lines.size(), () -> RichDiff.NO_REMARKS));
        Vector<Integer> starts = parsed.hunks()
                .scanLeft(0, (start, hunk) -> start + hunk.lines().size())
                .toVector();
        String bar = version.map(shown -> bar(parsed, shown)).getOrElse(() -> bar(file));
        HunkLayout layout = laid.apply(
                new Lines(fileAt, fold, drawing, bar, parsed.hunks().toVector(), lines, measured, onLines, starts));
        // The gap before each hunk, by the index of the hunk; the one after the last is before the hunk there is not.
        Map<Integer, Folding.Gap> gapBefore = (version.isDefined()
                        ? List.<Folding.Gap>empty()
                        : Folding.gaps(file, fold.length()))
                .toMap(Folding.Gap::before, gap -> gap);
        int count = parsed.hunks().size();
        Option<Folding.Gap> trailing = gapBefore.get(count);
        boolean unplaced =
                version.isEmpty() && remarks.exists(placed -> !placed.unplaced().isEmpty());
        boolean afterLast = unplaced || trailing.isDefined();
        Function<Folding.Gap, Pending> marker = gap -> marker(gap, fold, layout.markerAt());
        Function<String, Pending> dim =
                text -> () -> List.of(Marked.plain(Line.from(RichDiff.styled(text, Theme.Classes.DIM, styles))));
        return List.<Pending>of(() -> List.of(barLine(bar, file.path(), layout.width(), styles)))
                .appendAll(note(file).orElse(() -> allHidden(hiding)).toList().map(dim))
                .appendAll(remarks.toList()
                        .<Pending>map(placed -> () -> layout.blocks()
                                .threads(
                                        placed.onFile(),
                                        Option.none(),
                                        !file.hunks().isEmpty() || unplaced,
                                        styles)))
                .appendAll(hunks(
                        count,
                        hiding,
                        (at, headed) -> List.<Pending>of(() -> headed
                                        ? gapBefore
                                                .get(at)
                                                .map(gap -> marker.apply(gap).draw())
                                                .getOrElse(() -> layout.heading(at))
                                        : List.empty())
                                .appendAll(layout.hunk(at, afterLast || at < count - 1)),
                        at -> gapBefore.get(at).toList().map(marker),
                        (run, opened) -> filteredMarker(run, opened, layout.markerAt())))
                .appendAll(trailing.toList().map(marker))
                .appendAll(Option.when(
                                version.isDefined() && fold.read() == Expansion.Read.CUT,
                                () -> Messages.get("content.file.versionCut"))
                        .toList()
                        .map(dim))
                .appendAll(remarks.filter(ignored -> version.isEmpty())
                        .toList()
                        .<Pending>map(placed -> () -> layout.blocks().unplaced(placed.unplaced(), styles)))
                .<Pending>map(pending -> () -> pending.draw().map(layout::placed));
    }

    /**
     * The bar of the file at {@code path}, which says {@code bar}, a stop of the cursor, tinted {@code width} wide. It
     * is padded in a span of its own, which the area draws as far as its edge (ADR 80).
     */
    static Marked barLine(String bar, String path, int width, Theme.SpanStyles styles) {
        return Marked.of(
                Rows.kept(Line.from(
                        RichDiff.styled(bar, Theme.Classes.DIFF_FILE_HEADER, styles),
                        RichDiff.styled(
                                " ".repeat(Math.max(0, width - RichDiff.width(bar))),
                                Theme.Classes.DIFF_FILE_HEADER,
                                styles))),
                Option.of(new Mark(new Anchor.File(path), 1, width, Option.none())));
    }

    /**
     * What the bar of {@code file} says while {@code version} of it is shown in place of its diff (ADR 102): which
     * version, and its path in it, the one it had before a rename for the original. A file without a line that changed
     * is one without changes, shown as it is on disk (ADR 108).
     */
    static String bar(ParsedFile parsed, FileVersion version) {
        FileDiff file = parsed.file();
        String path = version == FileVersion.ORIGINAL ? file.origin().getOrElse(file.path()) : file.path();
        String what = Messages.get(
                unchanged(parsed)
                        ? "content.file.unchanged"
                        : version == FileVersion.NEW ? "content.file.newVersion" : "content.file.original");
        return " " + what + "  " + path + " ";
    }

    /**
     * What the bar of {@code file} says: what happened to it and its path, and how its mode changed if it did.
     */
    static String bar(FileDiff file) {
        String what = Messages.get(
                switch (file.type()) {
                    case ADDED -> "content.file.added";
                    case DELETED -> "content.file.deleted";
                    case RENAMED -> "content.file.renamed";
                    case CONFLICTED -> "content.file.conflicted";
                    default -> "content.file.modified";
                });
        String path = file.origin().map(origin -> origin + " → ").getOrElse("") + file.path();
        String mode = file.mode()
                .map(change -> "  " + Messages.get("content.file.mode", change.from(), change.to()))
                .getOrElse("");
        return " " + what + "  " + path + mode + " ";
    }

    /**
     * Whether no line of {@code parsed} was added or removed: a file without changes, shown as a diff of itself.
     */
    private static boolean unchanged(ParsedFile parsed) {
        return parsed.hunks()
                .flatMap(ParsedHunk::lines)
                .forAll(line -> line.line().kind() == DiffLine.Kind.CONTEXT
                        || line.line().kind() == DiffLine.Kind.NOTE);
    }

    /**
     * Why {@code file} has no lines to show, if it has none and that does not go without saying.
     */
    private static Option<String> note(FileDiff file) {
        return file.binary()
                ? Option.of(Messages.get("content.file.binary"))
                : Option.when(
                        file.hunks().isEmpty() && file.type() == ChangeType.RENAMED,
                        () -> Messages.get("content.file.renamedUnchanged"));
    }

    /**
     * That all the changes of a file are hidden, and by which filters, if they are (ADR 144).
     */
    private static Option<String> allHidden(Filtering.Hiding hiding) {
        return Option.when(
                hiding.all(), () -> Messages.get("content.filtered.all", String.join(", ", hiding.filters())));
    }

    /**
     * The lines still to be drawn of the {@code count} hunks of a file, as {@code hiding} hides them (ADR 144): each
     * hunk shown as {@code shown} draws it, and each run of hunks hidden with nothing shown between them as one marker,
     * {@code marker}, after the marker of the lines left out before the first of them, if there are some, as
     * {@code gap} draws it.
     */
    private static List<Pending> hunks(
            int count,
            Filtering.Hiding hiding,
            BiFunction<Integer, Boolean, List<Pending>> shown,
            IntFunction<List<Pending>> gap,
            BiFunction<List<HiddenHunks.Hidden>, Boolean, Pending> marker) {
        // Each hunk is shown, hidden, or opened: hidden and opened ones run on while the next is too.
        IntFunction<Integer> state =
                at -> hiding.of(at).isDefined() ? 1 : hiding.openedAt(at).isDefined() ? 2 : 0;
        List<List<Integer>> runs = List.range(0, count)
                .foldLeft(
                        List.<List<Integer>>empty(),
                        (done, at) -> state.apply(at) != 0
                                        && done.headOption()
                                                .exists(run ->
                                                        state.apply(run.last()).equals(state.apply(at))
                                                                && run.last() == at - 1)
                                ? done.tail().prepend(done.head().append(at))
                                : done.prepend(List.of(at)))
                .reverse();
        return runs.flatMap(run -> switch (state.apply(run.head())) {
            case 1 ->
                gap.apply(run.head())
                        .append(marker.apply(run.flatMap(hiding::of).distinctBy(HiddenHunks.Hidden::start), false));
            // The marker of hunks opened takes the place of the rule of the first, under the marker of the lines left
            // out before it, as the marker of hunks hidden does.
            case 2 ->
                gap.apply(run.head())
                        .append(marker.apply(run.flatMap(hiding::openedAt).distinctBy(HiddenHunks.Hidden::start), true))
                        .appendAll(shown.apply(run.head(), false))
                        .appendAll(run.tail().flatMap(at -> shown.apply(at, true)));
            default -> shown.apply(run.head(), true);
        });
    }

    /**
     * The marker of {@code run}, hunks of a file that filters hide, a stop of the cursor, where {@code at} says (ADR
     * 144): its rule, how many lines they change, and the filters that hide them, or that they are shown, when the user
     * {@code opened} them, which Enter on it hides again.
     */
    private static Pending filteredMarker(List<HiddenHunks.Hidden> run, boolean opened, MarkerAt at) {
        String says = Messages.get(
                opened ? "content.filtered.opened" : "content.filtered.marker",
                run.map(HiddenHunks.Hidden::changes).sum().intValue(),
                String.join(", ", run.flatMap(HiddenHunks.Hidden::filters).distinct()));
        return () -> List.of(Marked.of(
                Rows.kept(Line.from(
                        at.rule(), RichDiff.styled("┄ " + says + " ┄", Theme.Classes.DIFF_HUNK, at.styles()))),
                Option.of(new Mark(
                        new Anchor.Filtered(at.path(), run.map(HiddenHunks.Hidden::ranges), opened),
                        1,
                        at.width(),
                        Option.none()))));
    }

    /**
     * The marker of {@code gap}, the lines a file folded as {@code fold} says leaves out, a stop of the cursor, where
     * {@code at} says (ADR 77): the rule, and how many it leaves out, or that more follow when that is not known, or
     * that the file is being read whole, or could not be, or is too long to be shown past where its read was cut, once
     * the marker starts past it.
     */
    private static Pending marker(Folding.Gap gap, Expansion.Fold fold, MarkerAt at) {
        String says = switch (fold.read()) {
            case READING -> Messages.get("content.gap.reading");
            case FAILED -> Messages.get("content.gap.failed");
            case TOO_LONG -> Messages.get("content.gap.tooLong");
            case CUT, NOT, WHOLE ->
                gap.hidden()
                        .map(hidden -> Messages.get("content.gap.hidden", hidden))
                        // After the last hunk of a file read up to a cut, more can be shown until the cut.
                        .getOrElse(() ->
                                Messages.get(fold.pastTheCut(gap.from()) ? "content.gap.tooLong" : "content.gap.more"));
        };
        return () -> List.of(Marked.of(
                Rows.kept(Line.from(at.rule(), RichDiff.styled(says + " ┄", Theme.Classes.DIFF_HUNK, at.styles()))),
                Option.of(new Mark(new Anchor.Gap(at.path(), gap.from(), gap.until()), 1, at.width(), Option.none()))));
    }

    /**
     * The lines of a file, which its hunks are laid out from: the file {@code at}, folded as {@code fold} says, drawn
     * as {@code drawing} says, what its {@code bar} says, its {@code hunks}, all their {@code lines}, how wide each was
     * {@code measured}, the threads on each, {@code remarks}, and the index of the first line of each hunk in them,
     * {@code starts}, with that of the line after the last hunk at the end.
     */
    record Lines(
            FileAt at,
            Expansion.Fold fold,
            Drawing drawing,
            String bar,
            Vector<ParsedHunk> hunks,
            Vector<ParsedLine> lines,
            Vector<Integer> measured,
            Vector<LineRemarks> remarks,
            Vector<Integer> starts) {

        /**
         * The part of {@code all}, which has an element for each line of the file, that is of hunk {@code hunk}.
         */
        <T> Vector<T> inHunk(Vector<T> all, int hunk) {
            return all.slice(starts.get(hunk), starts.get(hunk + 1));
        }

        String path() {
            return at.parsed().file().path();
        }

        int index() {
            return at.index();
        }

        RemarkLines.RemarkStyle style() {
            return at.style();
        }

        Theme.SpanStyles styles() {
            return drawing.styles();
        }

        boolean has(RichDiff.Highlight highlight) {
            return drawing.has(highlight);
        }
    }

    /**
     * Where a marker goes in a file: in the file with the path {@code path}, after {@code rule}, tinted {@code width}
     * wide when the cursor is on it, in the {@code styles} of the theme.
     */
    record MarkerAt(String path, Span rule, int width, Theme.SpanStyles styles) {}
}
