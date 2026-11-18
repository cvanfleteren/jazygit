package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.content.diff.RichDiff.Blocks;
import net.vanfleteren.jazygit.content.diff.RichDiff.Change;
import net.vanfleteren.jazygit.content.diff.RichDiff.Mark;
import net.vanfleteren.jazygit.content.diff.RichDiff.Marked;
import net.vanfleteren.jazygit.content.diff.RichDiff.Pending;
import net.vanfleteren.jazygit.content.diff.RichDiff.Piece;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.git.model.LinePairs;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Filtering;
import net.vanfleteren.jazygit.state.diff.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.diff.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.syntax.Token;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * A file of a diff drawn by the split view (ADR 139): its bar, then each row of its hunks with the old file on the left
 * and the new one on the right, as {@link LinePairs#rows} lines them up, in an area as wide as given. Each half has a
 * gutter with the numbers of its own file, and its line drawn as the rich view draws it, whole: the area cuts each
 * half to its width ({@link Halves}). A thread goes under its row, in the half of its side.
 */
final class SplitRows {

    // Not a character of any line of the diff: what a search skips in a row, between and beside its halves.
    private static final char SKIPPED = '\0';
    // The tint of the half on the right reaches to its end, however far that is.
    private static final int TO_THE_END = Integer.MAX_VALUE / 2;

    private SplitRows() {}

    /**
     * The lines still to be drawn of {@code parsed}, the {@code index}th file of its diff, with the review threads
     * {@code remarks} on it, folded as {@code fold} says, its code coloured by its syntax if {@code syntax}, its changed
     * words highlighted if {@code words}, its threads drawn as {@code style} says, in an area {@code area} columns
     * wide.
     */
    static List<Pending> file(
            ParsedFile parsed,
            Option<FileRemarks> remarks,
            int index,
            Expansion.Fold fold,
            Filtering.Hiding hiding,
            boolean syntax,
            boolean words,
            RemarkLines.RemarkStyle style,
            Theme.SpanStyles styles,
            int area) {
        FileDiff file = parsed.file();
        Vector<ParsedLine> lines = parsed.hunks().flatMap(ParsedHunk::lines).toVector();
        int digits = lines.flatMap(line ->
                        line.line().oldNumber().toList().appendAll(line.line().newNumber()))
                .max()
                .map(number -> String.valueOf(number).length())
                .getOrElse(1);
        Vector<Integer> measured = RichDiff.inParallel(
                        lines.toList(), line -> RichDiff.width(line.line().text()))
                .toVector();
        Vector<LineRemarks> onLines = remarks.map(
                        placed -> placed.lines().flatMap(hunk -> hunk).toVector())
                .getOrElse(() -> Vector.fill(lines.size(), () -> RichDiff.NO_REMARKS));
        int left = (area - 1) / 2;
        int gutter = digits + RichDiff.GUTTER_END.length();
        Layout layout = new Layout(file.path(), index, digits, left, area - 1 - left, syntax, words, style, styles);
        Map<Integer, Folding.Gap> gapBefore = Folding.gaps(file, fold.length()).toMap(Folding.Gap::before, gap -> gap);
        Option<Folding.Gap> trailing = gapBefore.get(parsed.hunks().size());
        boolean unplaced = remarks.exists(placed -> !placed.unplaced().isEmpty());
        Blocks wide = new Blocks(gutter + 2, RemarkLines.blockWidth(area), style);
        Vector<Integer> starts = parsed.hunks()
                .scanLeft(0, (start, hunk) -> start + hunk.lines().size())
                .toVector();
        String bar = RichDiff.bar(file);
        return List.<Pending>of(() -> List.of(new Marked(
                                Rows.kept(Line.from(
                                        RichDiff.styled(bar, Theme.Classes.DIFF_FILE_HEADER, styles),
                                        RichDiff.styled(
                                                " ".repeat(Math.max(0, area - RichDiff.width(bar))),
                                                Theme.Classes.DIFF_FILE_HEADER,
                                                styles))),
                                Option.of(new Mark(new Anchor.File(file.path()), 1, area, Option.none()))))
                        .appendAll(RichDiff.note(file)
                                .orElse(() -> RichDiff.allHidden(hiding))
                                .map(text -> Marked.plain(Line.from(RichDiff.styled(text, Theme.Classes.DIM, styles)))))
                        .appendAll(remarks.toList()
                                .flatMap(placed -> wide.threads(
                                        placed.onFile(),
                                        Option.none(),
                                        !file.hunks().isEmpty() || unplaced,
                                        styles))))
                .appendAll(RichDiff.hunks(
                        parsed.hunks().size(),
                        hiding,
                        (at, headed) -> List.of(() -> {
                            ParsedHunk hunk = parsed.hunks().get(at);
                            int start = starts.get(at);
                            Hunk shown = new Hunk(
                                    hunk.lines().toVector(),
                                    measured.slice(start, starts.get(at + 1)),
                                    onLines.slice(start, starts.get(at + 1)),
                                    new DiffStops.HunkAt(index, at));
                            boolean followed = unplaced
                                    || trailing.isDefined()
                                    || at < parsed.hunks().size() - 1;
                            List<Marked> header = !headed
                                    ? List.<Marked>empty()
                                    : gapBefore
                                            .get(at)
                                            .map(gap -> marker(gap, fold, layout))
                                            .orElse(Option.when(at > 0, () -> List.of(Marked.plain(rule(layout)))))
                                            .getOrElse(List.empty());
                            List<LinePairs.Row> rows = LinePairs.rows(hunk.lines());
                            return header.appendAll(rows.zipWithIndex()
                                    .flatMap(row ->
                                            row(row._1(), shown, followed || row._2() < rows.size() - 1, layout)));
                        }),
                        at -> gapBefore.get(at).toList().<Pending>map(gap -> () -> marker(gap, fold, layout)),
                        (run, opened) -> RichDiff.filteredMarker(
                                run, opened, file.path(), ruleOf(layout), layout.area(), styles)))
                .appendAll(trailing.toList().map(gap -> () -> marker(gap, fold, layout)))
                .appendAll(remarks.toList().<Pending>map(placed -> () -> wide.unplaced(placed.unplaced(), styles)));
    }

    /**
     * The marker of {@code gap}, as wide as the area, its rule as wide as the gutter of a half.
     */
    private static List<Marked> marker(Folding.Gap gap, Expansion.Fold fold, Layout layout) {
        return RichDiff.marker(gap, fold, layout.path(), ruleOf(layout), layout.area(), layout.styles())
                .draw();
    }

    /**
     * The rule where a hunk starts, in the gutter of each half.
     */
    private static Line rule(Layout layout) {
        Span rule = ruleOf(layout);
        return Line.from(
                rule, Span.raw(" ".repeat(Math.max(0, layout.left() - rule.width()))), divider(layout.styles()), rule);
    }

    private static Span ruleOf(Layout layout) {
        return RichDiff.styled(
                "┄".repeat(layout.digits()) + RichDiff.GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, layout.styles());
    }

    private static Span divider(Theme.SpanStyles styles) {
        return RichDiff.styled(RichDiff.DIVIDER, Theme.Classes.DIFF_LINE_NUMBER, styles);
    }

    /**
     * The lines of {@code row} of {@code hunk}: the row, a stop on each side it has a line on, then the notes after
     * its lines, then the threads on them, each under the half of its side, followed by a blank line if anything is
     * {@code followed} by in the file.
     */
    private static List<Marked> row(LinePairs.Row row, Hunk hunk, boolean followed, Layout layout) {
        Half before = row.left().map(at -> half(hunk, at, Side.LEFT, layout)).getOrElse(() -> blank(layout.left()));
        Half after = row.right().map(at -> half(hunk, at, Side.RIGHT, layout)).getOrElse(() -> blank(layout.right()));
        Halves halves = new Halves(
                layout.left(),
                layout.right(),
                before.leadWidth(),
                before.codeWidth(),
                before.leadWidth() + before.codeWidth() + 1 + after.leadWidth(),
                after.codeWidth());
        int rule = halves.rule();
        List<Mark> marks = row.left()
                .flatMap(at -> stop(hunk, at, Side.LEFT, 0, rule, layout))
                .toList()
                .appendAll(row.right().flatMap(at -> stop(hunk, at, Side.RIGHT, rule + 1, TO_THE_END, layout)));
        Marked drawn = new Marked(
                () -> Line.from(before.spans()
                        .appendAll(before.code().get())
                        .append(divider(layout.styles()))
                        .appendAll(after.spans())
                        .appendAll(after.code().get())
                        .toJavaList()),
                marks,
                List.empty(),
                Option.some(layout.area() + halves.overflow()),
                () -> source(hunk, row, before, after),
                Option.some(halves));
        List<ReviewThread> threads = threadsOf(hunk, row);
        return List.of(drawn)
                .appendAll(notes(row, hunk, layout))
                .appendAll(
                        threads.isEmpty()
                                ? List.empty()
                                : List.of(Side.LEFT, Side.RIGHT)
                                        .map(side -> threads.filter(thread -> thread.side() == side))
                                        .filter(onSide -> !onSide.isEmpty())
                                        .flatMap(onSide -> threads(onSide, hunk, row, layout))
                                        .appendAll(followed ? List.of(Marked.plain(Line.from(""))) : List.empty()));
    }

    /**
     * The threads on the lines of {@code row}, those on a context line once.
     */
    private static List<ReviewThread> threadsOf(Hunk hunk, LinePairs.Row row) {
        return row.left()
                .toList()
                .appendAll(row.right().filter(at -> !row.left().contains(at)))
                .flatMap(at -> hunk.remarks().get(at).threads());
    }

    /**
     * The lines of {@code threads}, all on one side, under the half of that side, as wide as it, the rule between
     * the halves kept beside them.
     */
    private static List<Marked> threads(List<ReviewThread> threads, Hunk hunk, LinePairs.Row row, Layout layout) {
        Side side = threads.head().side();
        int gutter = layout.digits() + RichDiff.GUTTER_END.length() + 2;
        int indent = side == Side.LEFT ? gutter : layout.left() + 1 + gutter;
        int end = side == Side.LEFT ? layout.left() - 1 : layout.area() - 1;
        Option<Integer> on = side == Side.LEFT ? row.left() : row.right();
        Option<Anchor.OnLine> line =
                on.flatMap(at -> Anchor.of(layout.path(), hunk.lines().get(at).line()));
        return new Blocks(indent, Math.max(indent + 20, end), layout.style())
                .threads(threads, line, false, layout.styles())
                .map(marked -> marked.mapped(drawn -> ruled(drawn, layout)).onSide(side));
    }

    /**
     * {@code line}, a line of a thread, with the rule between the halves at its column, where the line has a space
     * or ends before it.
     */
    private static Line ruled(Line line, Layout layout) {
        List<Span> spans = List.ofAll(line.spans());
        List<Span> before = Halves.slice(spans, 0, layout.left());
        int used = before.map(Span::width).sum().intValue();
        List<Span> at = Halves.slice(spans, layout.left(), layout.left() + 1);
        boolean free = at.forAll(span -> span.content().isBlank());
        return free
                ? Line.from(before.append(Span.raw(" ".repeat(Math.max(0, layout.left() - used))))
                        .append(divider(layout.styles()))
                        .appendAll(Halves.slice(spans, layout.left() + 1, Integer.MAX_VALUE))
                        .toJavaList())
                : line;
    }

    /**
     * The rows of the notes after the lines of {@code row}, each in the half of its line.
     */
    private static List<Marked> notes(LinePairs.Row row, Hunk hunk, Layout layout) {
        int count = Math.max(row.leftNotes().size(), row.rightNotes().size());
        return List.range(0, count).map(at -> {
            Half before = row.leftNotes()
                    .drop(at)
                    .headOption()
                    .map(note -> note(hunk, note, layout))
                    .getOrElse(() -> blank(layout.left()));
            Half after = row.rightNotes()
                    .drop(at)
                    .headOption()
                    .map(note -> note(hunk, note, layout))
                    .getOrElse(() -> blank(layout.right()));
            Halves halves = new Halves(
                    layout.left(),
                    layout.right(),
                    before.leadWidth(),
                    before.codeWidth(),
                    before.leadWidth() + before.codeWidth() + 1 + after.leadWidth(),
                    after.codeWidth());
            Line line = Line.from(before.spans()
                    .appendAll(before.code().get())
                    .append(divider(layout.styles()))
                    .appendAll(after.spans())
                    .appendAll(after.code().get())
                    .toJavaList());
            return new Marked(
                    Rows.kept(line),
                    List.empty(),
                    List.empty(),
                    Option.some(layout.area() + halves.overflow()),
                    () -> Rows.Source.of(line),
                    Option.some(halves));
        });
    }

    private static Half note(Hunk hunk, int at, Layout layout) {
        String text = hunk.lines().get(at).line().text();
        int gutter = layout.digits() + RichDiff.GUTTER_END.length();
        return new Half(
                List.of(Span.raw(" ".repeat(gutter))),
                gutter,
                () -> List.of(RichDiff.styled(text, Theme.Classes.DIM, layout.styles())),
                RichDiff.width(text),
                "");
    }

    /**
     * The half of the line at {@code at} of {@code hunk} on {@code side}: its gutter and marker, then its code.
     */
    private static Half half(Hunk hunk, int at, Side side, Layout layout) {
        ParsedLine parsed = hunk.lines().get(at);
        DiffLine line = parsed.line();
        Theme.SpanStyles styles = layout.styles();
        Option<Integer> number = side == Side.LEFT ? line.oldNumber() : line.newNumber();
        String numbers = RichDiff.number(number, layout.digits());
        Style numbered = switch (line.kind()) {
            case ADDED -> styles.refined(Theme.Classes.DIFF_LINE_NUMBER, Theme.Classes.DIFF_ADDED_LINE_NUMBER);
            case REMOVED -> styles.refined(Theme.Classes.DIFF_LINE_NUMBER, Theme.Classes.DIFF_REMOVED_LINE_NUMBER);
            case CONTEXT, NOTE -> styles.of(Theme.Classes.DIFF_LINE_NUMBER);
        };
        LineRemarks remarks = hunk.remarks().get(at);
        boolean ends = remarks.threads().exists(thread -> thread.side() == side);
        List<Span> gutter = ends || remarks.covered()
                ? List.of(
                        Span.styled(numbers + " ", numbered),
                        RichDiff.styled(ends ? "●" : "┃", Theme.Classes.DIFF_THREAD, styles),
                        Span.styled(" ", numbered))
                : List.of(Span.styled(numbers + RichDiff.GUTTER_END, numbered));
        Option<Change> change = switch (line.kind()) {
            case ADDED -> Option.some(Change.ADDED);
            case REMOVED -> Option.some(Change.REMOVED);
            case CONTEXT, NOTE -> Option.none();
        };
        Span marker = change.map(
                        changed -> RichDiff.styled(changed.marker() + RichDiff.MARGIN, changed.lineClass(), styles))
                .getOrElse(() -> Span.raw(" " + RichDiff.MARGIN));
        List<Span> lead = gutter.append(marker);
        Option<Array<Token>> tokens = layout.syntax() ? parsed.tokens() : Option.none();
        int measured = hunk.measured().get(at);
        return new Half(
                lead,
                lead.map(Span::width).sum().intValue(),
                () -> code(parsed, tokens, change, layout),
                width(parsed, tokens, change, measured, layout),
                line.text());
    }

    /**
     * The code of {@code parsed}, its changed words highlighted as the rich view does, its tokens coloured.
     */
    private static List<Span> code(
            ParsedLine parsed, Option<Array<Token>> tokens, Option<Change> change, Layout layout) {
        Theme.SpanStyles styles = layout.styles();
        if (change.isEmpty()) {
            return tokens.map(lexed ->
                            RichDiff.pieces(parsed.words(), lexed).map(piece -> RichDiff.context(piece, styles)))
                    .getOrElse(() -> List.of(Span.raw(parsed.line().text())));
        }
        Change changed = change.get();
        boolean tinted = styles.of(changed.lineClass()).bg().isPresent();
        Option<Array<Token>> coloured = tokens.filter(ignored -> tinted);
        return RichDiff.pieces(parsed.words(), coloured.getOrElse(Array.empty()))
                .map(piece -> {
                    String cssClass = piece.changed() && layout.words() ? changed.wordClass() : changed.lineClass();
                    return coloured.isDefined()
                            ? RichDiff.changedCode(piece, cssClass, styles)
                            : RichDiff.styled(piece.text(), cssClass, styles);
                });
    }

    /**
     * How wide the code of {@code parsed} is drawn, {@code measured} as a whole: its pieces are measured each, as
     * pieces cut from a grapheme are narrower together than their text.
     */
    private static int width(
            ParsedLine parsed, Option<Array<Token>> tokens, Option<Change> change, int measured, Layout layout) {
        Option<Array<Token>> lexed = change.isEmpty()
                ? tokens
                : tokens.filter(ignored ->
                        layout.styles().of(change.get().lineClass()).bg().isPresent());
        List<Piece> pieces = RichDiff.pieces(parsed.words(), lexed.getOrElse(Array.empty()));
        return pieces.size() <= 1
                ? measured
                : pieces.map(piece -> RichDiff.width(piece.text())).sum().intValue();
    }

    /**
     * A half without a line, as wide as {@code width}, blank to the end of its block (ADR 139).
     */
    private static Half blank(int width) {
        return new Half(List.of(Span.raw(" ".repeat(width))), width, List::empty, 0, "");
    }

    /**
     * The stop of the line at {@code at} of {@code hunk} on {@code side}, its tint {@code width} columns from
     * {@code column}: a context line on the left by its number in the old file.
     */
    private static Option<Mark> stop(Hunk hunk, int at, Side side, int column, int width, Layout layout) {
        DiffLine line = hunk.lines().get(at).line();
        Option<Anchor> anchor = side == Side.LEFT && line.kind() == DiffLine.Kind.CONTEXT
                ? RichDiff.leftOfContext(layout.path(), line)
                : Anchor.of(layout.path(), line).map(on -> on);
        return anchor.map(
                on -> new Mark(on, 1, width, Option.some(hunk.at()), Option.some(side), column, Option.none(), at));
    }

    /**
     * What a row is searched in (ADR 126): the code of each half, from where the left one starts, the gutter of the
     * right one skipped. A context line is searched on the right only, as a match on both sides would be the same
     * text twice.
     */
    private static Rows.Source source(Hunk hunk, LinePairs.Row row, Half before, Half after) {
        boolean context = row.left().isDefined() && row.left().equals(row.right());
        String leftText = context ? skipped(before.text().length()) : before.text();
        int between =
                1 + after.spans().map(span -> span.content().length()).sum().intValue();
        int from = before.spans().map(span -> span.content().length()).sum().intValue();
        return new Rows.Source(leftText + skipped(between) + after.text(), from);
    }

    private static String skipped(int length) {
        return String.valueOf(SKIPPED).repeat(length);
    }

    /**
     * A half of a row: the spans of its gutter and marker, as wide as {@code leadWidth}, then its code, drawn when
     * the row is, as wide as {@code codeWidth}, the text of which is {@code text}.
     */
    private record Half(List<Span> spans, int leadWidth, Supplier<List<Span>> code, int codeWidth, String text) {}

    /**
     * The lines of a hunk with what drawing them takes: how wide each was measured, the threads on each, and where
     * the hunk is.
     */
    private record Hunk(
            Vector<ParsedLine> lines, Vector<Integer> measured, Vector<LineRemarks> remarks, DiffStops.HunkAt at) {}

    /**
     * What drawing each row of a file takes: its path, its index in the diff, how many digits its numbers have, how
     * wide the halves are, whether its code is coloured by its syntax and its changed words highlighted, how its
     * threads are drawn, and the styles of the theme.
     */
    private record Layout(
            String path,
            int file,
            int digits,
            int left,
            int right,
            boolean syntax,
            boolean words,
            RemarkLines.RemarkStyle style,
            Theme.SpanStyles styles) {

        int area() {
            return left + 1 + right;
        }
    }
}
