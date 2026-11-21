package net.vanfleteren.jazygit.kernel.content.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.git.model.LinePairs;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.kernel.content.diff.Pieces.Piece;
import net.vanfleteren.jazygit.kernel.content.diff.RichDiff.Change;
import net.vanfleteren.jazygit.kernel.content.diff.RichDiff.Highlight;
import net.vanfleteren.jazygit.kernel.content.diff.RichDiff.Pending;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.state.diff.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn.Mark;
import net.vanfleteren.jazygit.kernel.ui.content.Halves;
import net.vanfleteren.jazygit.kernel.ui.text.Rows;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.lib.syntax.Token;

/**
 * The hunks of a file laid out by the split view (ADR 139): each row with the old file on the left and the new one on
 * the right, as {@link LinePairs#rows} lines them up, in an area as wide as the file is drawn. Each half has a gutter
 * with the numbers of its own file, and its line drawn as the unified view draws it, whole: the area cuts each half to
 * its width ({@link Halves}). A thread goes under its row, in the half of its side. Each line of the file that is no
 * row is as wide as the area, and pinned.
 *
 * @param file   the lines of the file
 * @param digits how many digits the numbers of each half take
 * @param left   how wide the half on the left is
 * @param right  how wide the half on the right is
 */
record SplitHunk(FileSections.Lines file, int digits, int left, int right) implements HunkLayout {

    // Not a character of any line of the diff: what a search skips in a row, between and beside its halves.
    private static final char SKIPPED = '\0';
    // The tint of the half on the right reaches to its end, however far that is.
    private static final int TO_THE_END = Integer.MAX_VALUE / 2;

    /**
     * The hunks of {@code file} laid out side by side, in halves of an area as wide as it is drawn.
     */
    static SplitHunk of(FileSections.Lines file) {
        int area = file.drawing().width();
        int left = (area - 1) / 2;
        return new SplitHunk(file, Gutter.digits(file.lines(), file.fold()), left, area - 1 - left);
    }

    /**
     * As wide as the area: the halves and the rule between them.
     */
    @Override
    public int width() {
        return left + 1 + right;
    }

    @Override
    public Blocks blocks() {
        return new Blocks(digits + Gutter.END.length() + 2, RemarkLines.blockWidth(width()), file.style());
    }

    /**
     * Markers go after the rule, as wide as the gutter of a half, tinted as wide as the area.
     */
    @Override
    public FileSections.MarkerAt markerAt() {
        return new FileSections.MarkerAt(file.path(), ruleOf(), width(), styles());
    }

    /**
     * A rule in the gutter of each half, but before the first hunk, under the bar of the file.
     */
    @Override
    public List<Marked> heading(int at) {
        return at > 0 ? List.of(Marked.plain(rule())) : List.empty();
    }

    @Override
    public List<Pending> hunk(int at, boolean followed) {
        return List.of(() -> {
            ParsedHunk hunk = file.hunks().get(at);
            Hunk shown = new Hunk(
                    hunk.lines().toVector(),
                    file.inHunk(file.measured(), at),
                    file.inHunk(file.remarks(), at),
                    new DiffStops.HunkAt(file.index(), at));
            List<LinePairs.Row> rows = LinePairs.rows(hunk.lines());
            return rows.zipWithIndex().flatMap(row -> row(row._1(), shown, followed || row._2() < rows.size() - 1));
        });
    }

    /**
     * {@code line} as a row of the split view: one that is not a row of halves is as wide as the area, and pinned.
     */
    @Override
    public Marked placed(Marked line) {
        return line.split();
    }

    private Theme.SpanStyles styles() {
        return file.styles();
    }

    /**
     * The rule where a hunk starts, in the gutter of each half.
     */
    private Line rule() {
        Span rule = ruleOf();
        return Line.from(rule, Span.raw(" ".repeat(Math.max(0, left - rule.width()))), divider(), rule);
    }

    private Span ruleOf() {
        return Gutter.rule(digits, styles());
    }

    private Span divider() {
        return RichDiff.styled(RichDiff.DIVIDER, Theme.Classes.DIFF_LINE_NUMBER, styles());
    }

    /**
     * The lines of {@code row} of {@code hunk}: the row, a stop on each side it has a line on, then the notes after
     * its lines, then the threads on them, each under the half of its side, followed by a blank line if anything is
     * {@code followed} by in the file.
     */
    private List<Marked> row(LinePairs.Row row, Hunk hunk, boolean followed) {
        Half before = row.left().map(at -> half(hunk, at, DiffSide.OLD)).getOrElse(() -> blank(left));
        Half after = row.right().map(at -> half(hunk, at, DiffSide.NEW)).getOrElse(() -> blank(right));
        Halves halves = halves(before, after);
        int rule = halves.rule();
        List<Mark> marks = row.left()
                .flatMap(at -> stop(hunk, at, DiffSide.OLD, 0, rule))
                .toList()
                .appendAll(row.right().flatMap(at -> stop(hunk, at, DiffSide.NEW, rule + 1, TO_THE_END)));
        Marked drawn = new Marked.Split(
                () -> joined(before, after),
                marks,
                List.empty(),
                Option.some(width() + halves.overflow()),
                () -> source(row, before, after),
                Option.some(halves));
        List<RemarkThread> threads = threadsOf(hunk, row);
        return List.of(drawn)
                .appendAll(notes(row, hunk))
                .appendAll(
                        threads.isEmpty()
                                ? List.empty()
                                : List.of(DiffSide.OLD, DiffSide.NEW)
                                        .map(side -> threads.filter(thread -> thread.side() == side))
                                        .filter(onSide -> !onSide.isEmpty())
                                        .flatMap(onSide -> threads(onSide, hunk, row))
                                        .appendAll(followed ? List.of(Marked.plain(Line.from(""))) : List.empty()));
    }

    /**
     * Where the halves of a row of {@code before} and {@code after} are.
     */
    private Halves halves(Half before, Half after) {
        return new Halves(
                left,
                right,
                before.leadWidth(),
                before.codeWidth(),
                before.leadWidth() + before.codeWidth() + 1 + after.leadWidth(),
                after.codeWidth());
    }

    /**
     * The row of {@code before} and {@code after}, drawn, the rule between them.
     */
    private Line joined(Half before, Half after) {
        return Line.from(before.spans()
                .appendAll(before.code().get())
                .append(divider())
                .appendAll(after.spans())
                .appendAll(after.code().get())
                .toJavaList());
    }

    /**
     * The threads on the lines of {@code row}, those on a context line once.
     */
    private static List<RemarkThread> threadsOf(Hunk hunk, LinePairs.Row row) {
        return row.left()
                .toList()
                .appendAll(row.right().filter(at -> !row.left().contains(at)))
                .flatMap(at -> hunk.remarks().get(at).threads());
    }

    /**
     * The lines of {@code threads}, all on one side, under the half of that side, as wide as it, the rule between
     * the halves kept beside them.
     */
    private List<Marked> threads(List<RemarkThread> threads, Hunk hunk, LinePairs.Row row) {
        DiffSide side = threads.head().side();
        int gutter = digits + Gutter.END.length() + 2;
        int indent = side == DiffSide.OLD ? gutter : left + 1 + gutter;
        int end = side == DiffSide.OLD ? left - 1 : width() - 1;
        Option<Integer> on = side == DiffSide.OLD ? row.left() : row.right();
        Option<Anchor.OnLine> line =
                on.flatMap(at -> Anchor.of(file.path(), hunk.lines().get(at).line()));
        return new Blocks(indent, Math.max(indent + 20, end), file.style())
                .threads(threads, line, false, styles())
                .map(marked -> marked.mapped(this::ruled).onSide(side));
    }

    /**
     * {@code line}, a line of a thread, with the rule between the halves at its column, where the line has a space
     * or ends before it.
     */
    private Line ruled(Line line) {
        List<Span> spans = List.ofAll(line.spans());
        List<Span> before = Halves.slice(spans, 0, left);
        int used = before.map(Span::width).sum().intValue();
        List<Span> at = Halves.slice(spans, left, left + 1);
        boolean free = at.forAll(span -> span.content().isBlank());
        return free
                ? Line.from(before.append(Span.raw(" ".repeat(Math.max(0, left - used))))
                        .append(divider())
                        .appendAll(Halves.slice(spans, left + 1, Integer.MAX_VALUE))
                        .toJavaList())
                : line;
    }

    /**
     * The rows of the notes after the lines of {@code row}, each in the half of its line.
     */
    private List<Marked> notes(LinePairs.Row row, Hunk hunk) {
        int count = Math.max(row.leftNotes().size(), row.rightNotes().size());
        return List.range(0, count).map(at -> {
            Half before = row.leftNotes()
                    .drop(at)
                    .headOption()
                    .map(note -> note(hunk, note))
                    .getOrElse(() -> blank(left));
            Half after = row.rightNotes()
                    .drop(at)
                    .headOption()
                    .map(note -> note(hunk, note))
                    .getOrElse(() -> blank(right));
            Halves halves = halves(before, after);
            Line line = joined(before, after);
            return new Marked.Split(
                    Rows.kept(line),
                    List.empty(),
                    List.empty(),
                    Option.some(width() + halves.overflow()),
                    () -> Rows.Source.of(line),
                    Option.some(halves));
        });
    }

    private Half note(Hunk hunk, int at) {
        String text = hunk.lines().get(at).line().text();
        int gutter = digits + Gutter.END.length();
        return new Half(
                List.of(Span.raw(" ".repeat(gutter))),
                gutter,
                () -> List.of(RichDiff.styled(text, Theme.Classes.DIM, styles())),
                RichDiff.width(text),
                "");
    }

    /**
     * The half of the line at {@code at} of {@code hunk} on {@code side}: its gutter and marker, then its code.
     */
    private Half half(Hunk hunk, int at, DiffSide side) {
        ParsedLine parsed = hunk.lines().get(at);
        DiffLine line = parsed.line();
        Theme.SpanStyles styles = styles();
        String numbers = Gutter.number(side == DiffSide.OLD ? line.oldNumber() : line.newNumber(), digits);
        LineRemarks remarks = hunk.remarks().get(at);
        List<Span> gutter = Gutter.spans(
                numbers,
                line.kind(),
                remarks.threads().exists(thread -> thread.side() == side),
                remarks.covered(),
                styles);
        Option<Change> change = switch (line.kind()) {
            case ADDED -> Option.some(Change.ADDED);
            case REMOVED -> Option.some(Change.REMOVED);
            case CONTEXT, NOTE -> Option.none();
        };
        Span marker = change.map(
                        changed -> RichDiff.styled(changed.marker() + RichDiff.MARGIN, changed.lineClass(), styles))
                .getOrElse(() -> Span.raw(" " + RichDiff.MARGIN));
        List<Span> lead = gutter.append(marker);
        Option<Array<Token>> tokens = file.has(Highlight.SYNTAX) ? parsed.tokens() : Option.none();
        int measured = hunk.measured().get(at);
        return new Half(
                lead,
                lead.map(Span::width).sum().intValue(),
                () -> code(parsed, tokens, change),
                codeWidth(parsed, tokens, change, measured),
                line.text());
    }

    /**
     * The code of {@code parsed}, its changed words highlighted as the unified view does, its tokens coloured.
     */
    private List<Span> code(ParsedLine parsed, Option<Array<Token>> tokens, Option<Change> change) {
        Theme.SpanStyles styles = styles();
        if (change.isEmpty()) {
            return tokens.map(lexed -> Pieces.of(parsed.words(), lexed).map(piece -> Pieces.context(piece, styles)))
                    .getOrElse(() -> List.of(Span.raw(parsed.line().text())));
        }
        Change changed = change.get();
        boolean tinted = styles.of(changed.lineClass()).bg().isPresent();
        Option<Array<Token>> coloured = tokens.filter(ignored -> tinted);
        return Pieces.of(parsed.words(), coloured.getOrElse(Array.empty())).map(piece -> {
            String cssClass = piece.changed() && file.has(Highlight.WORDS) ? changed.wordClass() : changed.lineClass();
            return coloured.isDefined()
                    ? Pieces.changedCode(piece, cssClass, styles)
                    : RichDiff.styled(piece.text(), cssClass, styles);
        });
    }

    /**
     * How wide the code of {@code parsed} is drawn, {@code measured} as a whole: its pieces are measured each, as
     * pieces cut from a grapheme are narrower together than their text.
     */
    private int codeWidth(ParsedLine parsed, Option<Array<Token>> tokens, Option<Change> change, int measured) {
        Option<Array<Token>> lexed = change.isEmpty()
                ? tokens
                : tokens.filter(
                        ignored -> styles().of(change.get().lineClass()).bg().isPresent());
        List<Piece> pieces = Pieces.of(parsed.words(), lexed.getOrElse(Array.empty()));
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
    private Option<Mark> stop(Hunk hunk, int at, DiffSide side, int column, int width) {
        DiffLine line = hunk.lines().get(at).line();
        Option<Anchor> anchor = side == DiffSide.OLD && line.kind() == DiffLine.Kind.CONTEXT
                ? RichDiff.leftOfContext(file.path(), line)
                : Anchor.of(file.path(), line).map(on -> on);
        return anchor.map(
                on -> new Mark(on, 1, width, Option.some(hunk.at()), Option.some(side), column, Option.none(), at));
    }

    /**
     * What a row is searched in (ADR 126): the code of each half, from where the left one starts, the gutter of the
     * right one skipped. A context line is searched on the right only, as a match on both sides would be the same
     * text twice.
     */
    private static Rows.Source source(LinePairs.Row row, Half before, Half after) {
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
}
