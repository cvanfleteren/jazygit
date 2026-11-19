package net.vanfleteren.jazygit.kernel.content.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.Array;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.git.model.LinePairs;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.kernel.content.diff.Pieces.Piece;
import net.vanfleteren.jazygit.kernel.content.diff.RichDiff.Change;
import net.vanfleteren.jazygit.kernel.content.diff.RichDiff.Highlight;
import net.vanfleteren.jazygit.kernel.content.diff.RichDiff.Pending;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.state.diff.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn.Mark;
import net.vanfleteren.jazygit.kernel.ui.text.Rows;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.lib.syntax.Token;

/**
 * The hunks of a file laid out as in the unified view (ADR 44), or of a version of it shown in place of its diff (ADR
 * 102): each line under the one before it, after a gutter with its numbers in the old and the new file, in pairs where
 * they can be (ADR 83, {@link LinePairs}).
 *
 * @param file   the lines of the file
 * @param digits how many digits each column of its numbers takes
 * @param padded how many columns after the gutter its changed lines are padded to
 * @param width  how wide it is: its gutter and its widest line, or its bar if that is wider
 * @param blocks how the threads on it are drawn
 * @param blame  the column of its blame, if it is a version shown blamed (ADR 147)
 * @param rows   the rows of each of its hunks
 * @param groups the rows of each of its hunks drawn together
 */
record UnifiedHunk(
        FileSections.Lines file,
        int digits,
        int padded,
        int width,
        Blocks blocks,
        Option<BlameColumn> blame,
        Vector<Vector<Row>> rows,
        Vector<Vector<Group>> groups)
        implements HunkLayout {

    /**
     * The hunks of {@code file} laid out as in the unified view, as wide as its gutter and its widest line. The lines
     * of its hunk {@code asGiven}, and of a block a thread on several lines covers, are shown as git gives them, else
     * in pairs where they can be (ADR 83).
     */
    static UnifiedHunk of(FileSections.Lines file, Option<Integer> asGiven) {
        Vector<ParsedLine> lines = file.lines();
        boolean version = file.fold().version().isDefined();
        // A version shown blamed has the column of its blame before its numbers (ADR 147).
        Option<BlameColumn> blame =
                Gutter.blameColumn(lines, file.fold(), file.drawing().width(), file.styles());
        int gutter = blame.map(BlameColumn::width).getOrElse(0) + Gutter.numbersWidth(lines, file.fold());
        int width = Math.max(
                RichDiff.width(file.bar()),
                gutter
                        + file.measured()
                                .max()
                                .map(text -> text + 1 + RichDiff.MARGIN.length())
                                .getOrElse(0));
        Vector<Row> rows = Vector.range(0, lines.size())
                .map(at -> new Row(
                        lines.get(at), file.measured().get(at), file.remarks().get(at)));
        Vector<Vector<Row>> byHunk = Vector.range(0, file.hunks().size()).map(at -> file.inHunk(rows, at));
        // A block a thread covers stays as git gives it, so its gutter marks the lines the thread is on in a run.
        Vector<Vector<Group>> groups = file.hunks()
                .zipWithIndex((hunk, at) -> (asGiven.contains(at) || version
                                ? List.range(0, hunk.lines().size()).map(List::of)
                                : LinePairs.groups(
                                        hunk.lines(),
                                        line -> byHunk.get(at)
                                                .get(line)
                                                .remarks()
                                                .covered()))
                        .map(Group::new)
                        .toVector());
        return new UnifiedHunk(
                file,
                Gutter.digits(lines, file.fold()),
                width - gutter,
                width,
                new Blocks(gutter + 2, RemarkLines.blockWidth(width), file.style()),
                blame,
                byHunk,
                groups);
    }

    @Override
    public FileSections.MarkerAt markerAt() {
        return new FileSections.MarkerAt(file.path(), rule(), width, styles());
    }

    /**
     * A rule where a hunk starts, which stands in for git's {@code @@ -a,b +c,d @@}, whose numbers the gutter shows
     * already. Not the section heading git gives after them: it is a line from above the hunk, which reads as one of
     * its own. A header whose numbers cannot be read, whose lines are not numbered, follows the rule as git printed
     * it. The first hunk of a file has no rule, as the bar of the file marks where it starts, unless its header has to
     * be shown.
     */
    @Override
    public List<Marked> heading(int at) {
        Hunk hunk = file.hunks().get(at).hunk();
        return hunk.header().isEmpty() || (at == 0 && hunk.numbered())
                ? List.empty()
                : List.of(Marked.plain(
                        hunk.numbered()
                                ? Line.from(rule())
                                : Line.from(
                                        rule(), RichDiff.styled(hunk.header(), Theme.Classes.DIFF_HUNK, styles()))));
    }

    /**
     * The lines of hunk {@code at}, its rows cut into groups ({@link LinePairs}), each group followed by the threads
     * after its lines.
     */
    @Override
    public List<Pending> hunk(int at, boolean followed) {
        Hunk hunk = file.hunks().get(at).hunk();
        Vector<Row> shown = rows.get(at);
        Vector<Group> grouped = groups.get(at);
        // The lines of a hunk whose numbers cannot be read keep their prefix as git printed it, which a search
        // leaves out (ADR 126).
        int prefix = hunk.numbered() ? 0 : Rows.Source.prefix(hunk.header());
        // One for the hunk, which each of its lines holds: a long hunk has tens of thousands. The lines of a version
        // have none, so no range is started on them: a range is of the diff (ADR 102).
        Option<DiffStops.HunkAt> range = Option.when(version().isEmpty(), () -> new DiffStops.HunkAt(file.index(), at));
        // No line of the diff is wider than the bar of its file, which is as wide as the file: each is measured at most
        // that wide without being drawn.
        Option<Integer> within = Option.some(width);
        return grouped.zipWithIndex((group, g) -> (Pending) () -> {
                    List<Row> threaded = group.lines()
                            .map(shown::get)
                            .filter(row -> !row.remarks().threads().isEmpty());
                    return group.lines()
                            .<Marked>map(order -> {
                                Row member = shown.get(order);
                                DiffLine line = member.parsed().line();
                                Marked.Unified marked = new Marked.Unified(
                                        () -> line(member),
                                        Anchor.of(file.path(), line)
                                                .map(on -> new Mark(
                                                        on,
                                                        1,
                                                        width,
                                                        range,
                                                        Option.none(),
                                                        0,
                                                        RichDiff.leftOfContext(file.path(), line),
                                                        order))
                                                .toList(),
                                        List.empty(),
                                        within,
                                        () -> source(member, prefix),
                                        0);
                                // The gutter of a line of a version shown blamed stays where it is when the code
                                // scrolls sideways (ADR 147).
                                return blame.isDefined() ? marked.keeping(width - padded) : marked;
                            })
                            .appendAll(threaded.zipWithIndex()
                                    .flatMap(row -> blocks.threads(
                                            row._1().remarks().threads(),
                                            Anchor.of(
                                                    file.path(),
                                                    row._1().parsed().line()),
                                            followed || g < grouped.size() - 1 || row._2() < threaded.size() - 1,
                                            styles())));
                })
                .toList();
    }

    @Override
    public Marked placed(Marked line) {
        return line;
    }

    private Theme.SpanStyles styles() {
        return file.styles();
    }

    private Option<FileVersion> version() {
        return file.fold().version();
    }

    /**
     * The rule in the gutter that marks where a hunk starts.
     */
    private Span rule() {
        return Gutter.rule(2 * digits + 1, styles());
    }

    /**
     * The gutter of {@code row}: the column of its blame, if any, then the numbers of its line, and where a review
     * thread ends on it, or covers it, a mark (ADR 55).
     */
    private List<Span> gutter(Row row) {
        DiffLine line = row.parsed().line();
        LineRemarks remarks = row.remarks();
        return blamed(line)
                .appendAll(Gutter.spans(
                        numbers(line), line.kind(), !remarks.threads().isEmpty(), remarks.covered(), styles()));
    }

    /**
     * The column of the blame beside {@code line}, if the version shown is blamed (ADR 147).
     */
    private List<Span> blamed(DiffLine line) {
        return blame.map(column -> column.beside(version().flatMap(shown -> shown.number(line))))
                .getOrElse(List.empty());
    }

    /**
     * The numbers of {@code line} in its gutter: in the old and the new file, or in the version shown (ADR 102), in one
     * column when it is shown blamed (ADR 147).
     */
    private String numbers(DiffLine line) {
        return version()
                .map(shown -> blame.isDefined()
                        ? Gutter.number(shown.number(line), digits)
                        : Gutter.versionNumber(shown, line, digits))
                .getOrElse(
                        () -> Gutter.number(line.oldNumber(), digits) + " " + Gutter.number(line.newNumber(), digits));
    }

    /**
     * What the line of {@code row} is searched in (ADR 126): its text, without the first {@code prefix} characters
     * of a line kept as git printed it, after its gutter and its marker, which a note has none of. Its gutter is as
     * long whatever marks are in it ({@link #gutter}), after the column of its blame, if any, which is not searched
     * (ADR 147).
     */
    private Rows.Source source(Row row, int prefix) {
        DiffLine line = row.parsed().line();
        int marker = line.kind() == DiffLine.Kind.NOTE ? 0 : 1 + RichDiff.MARGIN.length();
        Rows.Source text = Rows.Source.cut(line.text(), prefix);
        return new Rows.Source(
                text.text(),
                BlameColumn.length(blamed(line)) + numbers(line).length() + Gutter.END.length() + marker + text.from());
    }

    /**
     * The line of {@code row} after its gutter, with its segments, and its tokens if it was lexed and the file's code
     * is coloured. Changed lines are padded to {@link #padded}, so their background makes a block, which the area
     * draws as far as its edge (ADR 80).
     */
    private Line line(Row row) {
        DiffLine line = row.parsed().line();
        List<Span> gutter = gutter(row);
        Option<Array<Token>> tokens = file.has(Highlight.SYNTAX) ? row.parsed().tokens() : Option.none();
        return switch (line.kind()) {
            case CONTEXT ->
                tokens.map(lexed -> Line.from(Iterator.concat(
                                        gutter,
                                        List.of(Span.raw(" " + RichDiff.MARGIN)),
                                        Pieces.of(row.parsed().words(), lexed)
                                                .iterator()
                                                .map(piece -> Pieces.context(piece, styles())))
                                .toJavaList()))
                        .getOrElse(() -> Line.from(
                                Iterator.concat(gutter, List.of(Span.raw(" " + RichDiff.MARGIN + line.text())))
                                        .toJavaList()));
            case NOTE ->
                Line.from(Iterator.concat(gutter, List.of(RichDiff.styled(line.text(), Theme.Classes.DIM, styles())))
                        .toJavaList());
            case ADDED -> changed(row, gutter, tokens, Change.ADDED);
            case REMOVED -> changed(row, gutter, tokens, Change.REMOVED);
        };
    }

    /**
     * The changed line of {@code row}: its marker and its unchanged parts in the line class of {@code change}, its
     * changed words in its word class if they are highlighted, padded to {@link #padded}. Its code is coloured by its
     * {@code tokens} if it was lexed, unless the line class gives it no background: then its colour is what tells it
     * is changed, and a line of tokens only would look like context. In a version of the file, its marker is blank
     * when the line class gives it a background, which tells it is changed already (ADR 102).
     */
    private Line changed(Row row, List<Span> gutter, Option<Array<Token>> tokens, Change change) {
        Theme.SpanStyles styles = styles();
        boolean tinted = styles.of(change.lineClass()).bg().isPresent();
        Option<Array<Token>> coloured = tokens.filter(ignored -> tinted);
        String marker = tinted && version().isDefined() ? " " : change.marker();
        List<Piece> pieces = Pieces.of(row.parsed().words(), coloured.getOrElse(Array.empty()));
        // Each piece is drawn as a span by itself, and pieces cut from a grapheme, like an emoji from its variation
        // selector, are narrower together than their text: only a line of one piece is as wide as it was measured.
        int used = 1
                + RichDiff.MARGIN.length()
                + (pieces.size() == 1
                        ? row.measured()
                        : pieces.map(piece -> RichDiff.width(piece.text()))
                                .sum()
                                .intValue());
        return Line.from(Iterator.concat(
                        gutter,
                        List.of(RichDiff.styled(marker + RichDiff.MARGIN, change.lineClass(), styles)),
                        pieces.iterator().map(piece -> {
                            String cssClass = piece.changed() && file.has(Highlight.WORDS)
                                    ? change.wordClass()
                                    : change.lineClass();
                            return coloured.isDefined()
                                    ? Pieces.changedCode(piece, cssClass, styles)
                                    : RichDiff.styled(piece.text(), cssClass, styles);
                        }),
                        List.of(RichDiff.styled(" ".repeat(Math.max(0, padded - used)), change.lineClass(), styles)))
                .toJavaList());
    }

    /**
     * A line of a file with what drawing it takes besides the layout of its file: how many columns its text was
     * {@code measured} at, and the review threads on it.
     */
    record Row(ParsedLine parsed, int measured, LineRemarks remarks) {}

    /**
     * The indexes of the lines of a hunk drawn together, as {@link LinePairs} groups them.
     */
    record Group(List<Integer> lines) {}
}
