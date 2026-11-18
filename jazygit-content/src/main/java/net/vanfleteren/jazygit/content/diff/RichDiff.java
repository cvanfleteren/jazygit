package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.git.model.HiddenHunks;
import net.vanfleteren.jazygit.git.model.LinePairs;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Filtering;
import net.vanfleteren.jazygit.state.diff.PlacedRemarks;
import net.vanfleteren.jazygit.state.diff.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.diff.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.state.diff.Rendered;
import net.vanfleteren.jazygit.syntax.Token;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * A unified diff drawn rich (ADR 44): what comes before its first file as it is, then each file under a bar that
 * says what happened to it, its lines numbered in the old and the new file, and the words that changed in a changed
 * line highlighted.
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

    /**
     * The gutter of a version rendered: the bar beside a block that changed, and a space (ADR 103).
     */
    static final int RENDERED_GUTTER = 2;

    static final String GUTTER_END = " │ ";
    // The rule between the halves of the split view.
    static final String DIVIDER = "│";
    /**
     * What sets the text of a line apart from its marker, as GitHub does: {@code + - item} reads as a list item that
     * was added, where {@code +- item} reads as neither.
     */
    static final String MARGIN = " ";

    static final LineRemarks NO_REMARKS = new LineRemarks(List.empty(), false);
    static final Pending BLANK = () -> List.of(Marked.plain(Line.from("")));

    private static final String RENDERED_BAR = "▎";

    private RichDiff() {}

    /**
     * The lines of {@code diff}, styled, its code coloured by its syntax if {@code syntax} (ADR 53), and the words
     * that changed in a changed line highlighted if {@code words}, else the whole line in its line class. It was
     * parsed, its words compared and its code lexed when it was read (ADR 50), so drawing it only styles it. The
     * review threads of a pull request, {@code remarks}, are drawn with it if it has them (ADR 55): each after its
     * line, which the gutter marks, one about a whole file under its bar, and the ones that cannot be placed at the
     * end of their file. It comes with where each stop of the cursor starts in the lines (ADR 60): the bar of each
     * file, each line that has a side and a number, each thread, and each comment of an open thread but its first.
     * The lines of a hunk are shown in pairs where they can be (ADR 83, {@link LinePairs}).
     */
    static Drawn drawn(
            ParsedDiff diff, boolean syntax, boolean words, Option<ShownRemarks> remarks, Theme.SpanStyles styles) {
        return drawn(Expansion.Folded.of(diff), syntax, words, remarks, Option.none(), styles);
    }

    /**
     * The lines of the diff of {@code folded}, as {@link #drawn(ParsedDiff, boolean, boolean, Option,
     * Theme.SpanStyles)} draws them, with a marker for the lines each of its files leaves out, as far as
     * {@code folded} knows them (ADR 77), and the lines of {@code asGiven}, the hunk a range is open in, as git gives
     * them, not in pairs (ADR 83).
     */
    static Drawn drawn(
            Expansion.Folded folded,
            boolean syntax,
            boolean words,
            Option<ShownRemarks> remarks,
            Option<DiffStops.HunkAt> asGiven,
            Theme.SpanStyles styles) {
        return drawn(folded, syntax, words, remarks, asGiven, styles, RENDERED_WIDTH);
    }

    /**
     * The lines of the diff of {@code folded}, as {@link #drawn(Expansion.Folded, boolean, boolean, Option, Option,
     * Theme.SpanStyles)} draws them, a version of a file rendered (ADR 103) {@code width} columns wide, its gutter
     * included.
     */
    public static Drawn drawn(
            Expansion.Folded folded,
            boolean syntax,
            boolean words,
            Option<ShownRemarks> remarks,
            Option<DiffStops.HunkAt> asGiven,
            Theme.SpanStyles styles,
            int width) {
        return drawn(folded, syntax, words, remarks, asGiven, styles, width, false);
    }

    /**
     * The lines of the diff of {@code folded}, as {@link #drawn(Expansion.Folded, boolean, boolean, Option, Option,
     * Theme.SpanStyles, int)} draws them, with the old file and the new one side by side if {@code split} (ADR 139),
     * the area {@code width} columns wide: unified, with a note that says why, when the area is narrower than
     * {@link #SPLIT_WIDTH}.
     */
    public static Drawn drawn(
            Expansion.Folded folded,
            boolean syntax,
            boolean words,
            Option<ShownRemarks> remarks,
            Option<DiffStops.HunkAt> asGiven,
            Theme.SpanStyles styles,
            int width,
            boolean split) {
        Option<Integer> sideBySide = Option.when(split && width >= SPLIT_WIDTH, width);
        Drawn drawn = drawn(folded, syntax, words, remarks, asGiven, styles, width, sideBySide);
        return split && sideBySide.isEmpty()
                ? drawn.withNote(Messages.get("content.split.narrow", SPLIT_WIDTH))
                : drawn;
    }

    private static Drawn drawn(
            Expansion.Folded folded,
            boolean syntax,
            boolean words,
            Option<ShownRemarks> remarks,
            Option<DiffStops.HunkAt> asGiven,
            Theme.SpanStyles styles,
            int width,
            Option<Integer> split) {
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
        // Each file is measured on a thread of its own, and then each of its lines is drawn on one, so that a diff of
        // many files and one of a long file both hold up the render thread for less.
        List<Pending> pending = inParallel(
                        diff.files().zip(placed).zipWithIndex(),
                        file -> file(
                                file._1()._1(),
                                file._1()._2(),
                                file._2(),
                                folded.fold(file._1()._1().file().path()),
                                folded.hiding(file._1()._1().file().path()),
                                asGiven.filter(hunk -> hunk.file() == file._2()).map(DiffStops.HunkAt::hunk),
                                syntax,
                                words,
                                style,
                                styles,
                                width,
                                split))
                .intersperse(List.of(BLANK))
                .flatMap(file -> file);
        Drawn drawn = Drawn.of(parsed.preamble()
                .map(line -> new Marked(() -> Line.from(Span.raw(line)), Option.none()))
                .appendAll(inParallel(pending, Pending::draw).flatMap(lines -> lines)));
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
                .flatMap(file -> blameColumn(
                                file.hunks().flatMap(ParsedHunk::lines).toVector(),
                                folded.fold(file.file().path()),
                                width,
                                styles)
                        .flatMap(BlameColumn::note))
                .headOption()
                .map(filtered::withNote)
                .getOrElse(filtered);
        return split.map(area -> noted.withDivider((area - 1) / 2)).getOrElse(noted);
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
     * {@code all} cut, in order, into parts of {@code sizes}.
     */
    private static <T> Vector<Vector<T>> cut(Vector<T> all, List<Integer> sizes) {
        return sizes.foldLeft(
                        Tuple.of(Vector.<Vector<T>>empty(), 0),
                        (done, size) -> Tuple.of(done._1.append(all.slice(done._2, done._2 + size)), done._2 + size))
                ._1;
    }

    /**
     * The lines still to be drawn of {@code parsed}, with the review threads {@code remarks} on it, its code coloured
     * by its syntax if {@code syntax}, its changed words highlighted if {@code words}, its threads drawn as {@code style} says: its bar, the threads on it, each of its
     * lines with those after it, once the width of its gutter and its lines is known, and the threads that cannot be
     * placed. The threads on a line are followed by a blank line, unless nothing follows them in the file. The lines of
     * its hunk {@code asGiven}, and of a block a thread on several lines covers, are shown as git gives them, else in
     * pairs where they can be (ADR 83). With the old file and the new one side by side in an area {@code split}
     * columns wide, if it is (ADR 139), but a version of it or a conflicted file.
     */
    private static List<Pending> file(
            ParsedFile parsed,
            Option<FileRemarks> remarks,
            int index,
            Expansion.Fold fold,
            Filtering.Hiding hiding,
            Option<Integer> asGiven,
            boolean syntax,
            boolean words,
            RemarkLines.RemarkStyle style,
            Theme.SpanStyles styles,
            int rendering,
            Option<Integer> split) {
        FileDiff file = parsed.file();
        Option<FileVersion> version = fold.version();
        if (fold.rendered() && version.isDefined() && Rendered.of(file.path()).isDefined()) {
            return List.of(() -> rendered(parsed, version.get(), fold, rendering, styles));
        }
        if (split.isDefined()
                && version.isEmpty()
                && parsed.hunks().forAll(hunk -> hunk.hunk().numbered())) {
            return SplitRows.file(parsed, remarks, index, fold, hiding, syntax, words, style, styles, split.get())
                    .map(pending -> () -> pending.draw().map(Marked::pin));
        }
        Vector<ParsedLine> lines = parsed.hunks().flatMap(ParsedHunk::lines).toVector();
        int digits = digits(lines, fold);
        // A version shown blamed has the column of its blame before its numbers (ADR 147).
        Option<BlameColumn> blame = blameColumn(lines, fold, rendering, styles);
        int gutter = blame.map(BlameColumn::width).getOrElse(0) + numbersWidth(lines, fold);
        String bar = version.map(shown -> bar(parsed, shown)).getOrElse(() -> bar(file));
        // Each line is measured once, here: its width also tells how far a changed line is padded.
        Vector<Integer> measured =
                inParallel(lines.toList(), line -> width(line.line().text())).toVector();
        int width = Math.max(
                width(bar),
                gutter + measured.max().map(text -> text + 1 + MARGIN.length()).getOrElse(0));
        Blocks blocks = new Blocks(gutter + 2, RemarkLines.blockWidth(width), style);
        Layout layout = new Layout(
                file.path(), index, digits, width - gutter, width, blocks, syntax, words, styles, version, blame);
        Vector<LineRemarks> onLines = remarks.map(
                        placed -> placed.lines().flatMap(hunk -> hunk).toVector())
                .getOrElse(() -> Vector.fill(lines.size(), () -> NO_REMARKS));
        // A version leaves no line out; the threads on the other version's lines are not shown with it.
        // The gap before each hunk, by the index of the hunk; the one after the last is before the hunk there is not.
        Map<Integer, Folding.Gap> gapBefore = (version.isDefined()
                        ? List.<Folding.Gap>empty()
                        : Folding.gaps(file, fold.length()))
                .toMap(Folding.Gap::before, gap -> gap);
        Option<Folding.Gap> trailing = gapBefore.get(parsed.hunks().size());
        boolean unplaced =
                version.isEmpty() && remarks.exists(placed -> !placed.unplaced().isEmpty());
        Option<String> cut = Option.when(
                version.isDefined() && fold.read() == Expansion.Read.CUT,
                () -> Messages.get("content.file.versionCut"));
        boolean afterLast = unplaced || trailing.isDefined();
        Vector<Row> rows =
                Vector.range(0, lines.size()).map(at -> new Row(lines.get(at), measured.get(at), onLines.get(at)));
        Vector<Vector<Row>> byHunk =
                cut(rows, parsed.hunks().map(hunk -> hunk.lines().size()));
        // A block a thread covers stays as git gives it, so its gutter marks the lines the thread is on in a run.
        List<List<List<Integer>>> groups = parsed.hunks()
                .zipWithIndex((hunk, at) -> asGiven.contains(at) || version.isDefined()
                        ? List.range(0, hunk.lines().size()).map(List::of)
                        : LinePairs.groups(
                                hunk.lines(),
                                line -> byHunk.get(at).get(line).remarks().covered()));
        // The bar is padded in a span of its own, which the area draws as far as its edge (ADR 80).
        return List.<Pending>of(() -> List.of(new Marked(
                                Rows.kept(Line.from(
                                        styled(bar, Theme.Classes.DIFF_FILE_HEADER, styles),
                                        styled(
                                                " ".repeat(Math.max(0, width - width(bar))),
                                                Theme.Classes.DIFF_FILE_HEADER,
                                                styles))),
                                Option.of(new Mark(new Anchor.File(file.path()), 1, width, Option.none()))))
                        .appendAll(note(file)
                                .orElse(() -> allHidden(hiding))
                                .map(text -> Marked.plain(Line.from(styled(text, Theme.Classes.DIM, styles))))))
                .appendAll(remarks.toList()
                        .<Pending>map(placed -> () -> blocks.threads(
                                placed.onFile(), Option.none(), !file.hunks().isEmpty() || unplaced, styles)))
                .appendAll(hunks(
                        parsed.hunks().size(),
                        hiding,
                        (at, headed) -> hunk(
                                parsed.hunks().get(at).hunk(),
                                byHunk.get(at),
                                groups.get(at),
                                at,
                                afterLast || at < parsed.hunks().size() - 1,
                                gapBefore.get(at),
                                headed,
                                fold,
                                layout),
                        at -> gapBefore.get(at).toList().map(gap -> marker(gap, fold, layout)),
                        (run, opened) ->
                                filteredMarker(run, opened, file.path(), rule(layout), layout.fileWidth(), styles)))
                .appendAll(trailing.toList().map(gap -> marker(gap, fold, layout)))
                .appendAll(cut.toList()
                        .<Pending>map(text ->
                                () -> List.of(Marked.plain(Line.from(styled(text, Theme.Classes.DIM, styles))))))
                .appendAll(remarks.filter(ignored -> version.isEmpty())
                        .toList()
                        .<Pending>map(placed -> () -> blocks.unplaced(placed.unplaced(), styles)));
    }

    /**
     * How many digits each column of numbers of the gutter of {@code lines}, a file folded as {@code fold} says, takes:
     * as many as its widest number. A version keeps the gutter of the diff it is shown in place of, as wide, so its
     * code does not move sideways (ADR 102): its numbers, of the whole file, may be longer than the diff's, but fit in
     * both of its columns.
     */
    private static int digits(Vector<ParsedLine> lines, Expansion.Fold fold) {
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
    private static boolean shownBlamed(Expansion.Fold fold) {
        return fold.version().isDefined() && fold.blame().isDefined();
    }

    /**
     * How many columns the numbers of the gutter of {@code lines}, a file folded as {@code fold} says, take, with its
     * end: two columns of {@link #digits} digits and a space between them, or one for a version shown blamed.
     */
    private static int numbersWidth(Vector<ParsedLine> lines, Expansion.Fold fold) {
        int digits = digits(lines, fold);
        return (shownBlamed(fold) ? digits : 2 * digits + 1) + GUTTER_END.length();
    }

    /**
     * The column of the blame of {@code lines}, a file folded as {@code fold} says, in an area {@code area} columns
     * wide: if it is a version shown blamed, as source (ADR 147).
     */
    private static Option<BlameColumn> blameColumn(
            Vector<ParsedLine> lines, Expansion.Fold fold, int area, Theme.SpanStyles styles) {
        return fold.version().flatMap(ignored -> BlameColumn.of(fold.blame(), area, numbersWidth(lines, fold), styles));
    }

    /**
     * {@code version} of {@code parsed}, a file read whole as {@code fold} says, rendered {@code width} columns wide,
     * its gutter included (ADR 103): its bar, a stop of the cursor whose lines the rows after it are, then each of its
     * blocks, with a bar in the gutter beside one that holds a line that changed, the lines each was drawn from placed
     * on its first row, and a line that says so when the file was read up to a cut.
     */
    private static List<Marked> rendered(
            ParsedFile parsed, FileVersion version, Expansion.Fold fold, int width, Theme.SpanStyles styles) {
        FileDiff file = parsed.file();
        String path = file.path();
        Vector<DiffLine> lines = parsed.hunks()
                .flatMap(ParsedHunk::lines)
                .map(ParsedLine::line)
                .filter(line -> line.kind() != DiffLine.Kind.NOTE)
                .toVector();
        Change change = version == FileVersion.NEW ? Change.ADDED : Change.REMOVED;
        // A theme that tints a changed line has a bar in the colour of its marker, green for an added one as GitHub's
        // is: the tint itself is a background, too dark to be seen as a bar. One that does not tint, as mono, has the
        // marker the line would have.
        Span changed = styled(
                styles.of(change.lineClass()).bg().isPresent() ? RENDERED_BAR : change.marker(),
                change.markerClass(),
                styles);
        Span unchanged = Span.raw(" ");
        String bar = bar(parsed, version);
        int tinted = Math.max(width(bar), width);
        Marked header = new Marked(
                Rows.kept(Line.from(
                        styled(bar, Theme.Classes.DIFF_FILE_HEADER, styles),
                        styled(" ".repeat(Math.max(0, tinted - width(bar))), Theme.Classes.DIFF_FILE_HEADER, styles))),
                Option.of(new Mark(new Anchor.File(path), 1, tinted, Option.none())));
        Option<Rendered> tree = Rendered.of(path).filter(Rendered::tree);
        if (tree.isDefined()) {
            return tree(tree.get(), path, lines, fold, width, tinted, changed, unchanged, styles)
                    .prepend(header);
        }
        Vector<RenderedMarkdown.Block> blocks = RenderedMarkdown.blocks(
                        lines.map(DiffLine::text), Math.max(1, width - RENDERED_GUTTER), styles)
                .toVector();
        Vector<Marked> body = blocks.zipWithIndex().flatMap(at -> {
            RenderedMarkdown.Block block = at._1();
            Span gutter = Vector.rangeClosed(block.first(), block.last())
                            .exists(line ->
                                    line < lines.size() && lines.get(line).kind() != DiffLine.Kind.CONTEXT)
                    ? changed
                    : unchanged;
            // A line no block holds, as a blank one between two, goes with the block after it, or the last block; the
            // block's own lines come first, so a key acts on its first line (ADR 103).
            int after = at._2() == 0 ? 0 : blocks.get(at._2() - 1).last() + 1;
            int until = at._2() == blocks.size() - 1 ? lines.size() - 1 : block.last();
            List<Anchor> placed = Vector.rangeClosed(block.first(), block.last())
                    .appendAll(Vector.range(after, block.first()))
                    .appendAll(Vector.rangeClosed(block.last() + 1, until))
                    .filter(line -> line < lines.size())
                    .flatMap(line -> placedBy(path, lines.get(line)))
                    .toList();
            List<Marked> rows = block.rows()
                    .zipWithIndex()
                    .map(row -> new Marked(
                            Rows.kept(Line.from(List.of(gutter, Span.raw(" "))
                                    .appendAll(row._1().spans())
                                    .toJavaList())),
                            Option.none(),
                            row._2() == 0 ? placed : List.empty()));
            return block.spaced() ? rows.prepend(Marked.plain(Line.from(""))) : rows;
        });
        return List.of(header)
                .appendAll(body)
                .appendAll(Option.when(
                                fold.read() == Expansion.Read.CUT,
                                () -> Marked.plain(Line.from(
                                        styled(Messages.get("content.file.renderedCut"), Theme.Classes.DIM, styles))))
                        .toList());
    }

    /**
     * The rows of a version of JSON or YAML rendered, a file of {@code kind}, its source {@code lines}, read as
     * {@code fold} says, {@code width} columns wide, its gutter included (ADR 122, 123): each a stop of the cursor, but
     * the keys of a table, with a bar in the gutter when a line it stands for changed, those lines placed on it. A file
     * that cannot be read is its source, after a line that says why, its lines placed on them, and no stops but the bar
     * of the file.
     */
    private static List<Marked> tree(
            Rendered kind,
            String path,
            Vector<DiffLine> lines,
            Expansion.Fold fold,
            int width,
            int tinted,
            Span changed,
            Span unchanged,
            Theme.SpanStyles styles) {
        Function<Integer, Option<DiffLine>> line =
                number -> Option.when(number >= 1 && number <= lines.size(), () -> lines.get(number - 1));
        Function<List<Integer>, Span> gutter = numbers -> numbers.exists(
                        number -> line.apply(number).exists(diffLine -> diffLine.kind() != DiffLine.Kind.CONTEXT))
                ? changed
                : unchanged;
        Function<List<Integer>, List<Anchor>> placed =
                numbers -> numbers.flatMap(line::apply).flatMap(diffLine -> placedBy(path, diffLine));
        return RenderedTree.rows(
                        kind,
                        lines.map(DiffLine::text),
                        fold.read() == Expansion.Read.CUT,
                        Math.max(1, width - RENDERED_GUTTER),
                        fold.nodes(),
                        styles)
                .fold(
                        why -> {
                            LexedHighlighter lexed = new LexedHighlighter(styles);
                            List<Marked> source = lines.zipWithIndex()
                                    .map(at -> new Marked(
                                            Rows.kept(Line.from(List.of(
                                                            gutter.apply(List.of(at._2() + 1)), Span.raw(" "))
                                                    .appendAll(List.ofAll(lexed.highlight(
                                                                    at._1().text(), kind.language(), Style.EMPTY, null))
                                                            .flatMap(drawn -> drawn.spans()))
                                                    .toJavaList())),
                                            Option.none(),
                                            placed.apply(List.of(at._2() + 1))))
                                    .toList();
                            return source.prepend(Marked.plain(Line.from(styled(why, Theme.Classes.DIM, styles))));
                        },
                        rows -> rows.map(row -> new Marked(
                                Rows.kept(Line.from(List.of(gutter.apply(row.lines()), Span.raw(" "))
                                        .appendAll(row.line().spans())
                                        .toJavaList())),
                                row.pointer()
                                        .flatMap(pointer -> line.apply(
                                                        row.lines().head())
                                                .flatMap(diffLine -> Anchor.of(path, diffLine))
                                                .map(on -> new Mark(
                                                        new Anchor.Node(path, pointer, on), 1, tinted, Option.none()))),
                                placed.apply(row.lines()))));
    }

    /**
     * What the bar of {@code file} says while {@code version} of it is shown in place of its diff (ADR 102): which
     * version, and its path in it, the one it had before a rename for the original. A file without a line that changed
     * is one without changes, shown as it is on disk (ADR 108).
     */
    private static String bar(ParsedFile parsed, FileVersion version) {
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
    static Option<String> note(FileDiff file) {
        return file.binary()
                ? Option.of(Messages.get("content.file.binary"))
                : Option.when(
                        file.hunks().isEmpty() && file.type() == ChangeType.RENAMED,
                        () -> Messages.get("content.file.renamedUnchanged"));
    }

    /**
     * The lines still to be drawn of {@code hunk}, its {@code rows} cut into {@code groups} ({@link LinePairs}), each
     * group followed by the threads after its lines, under a rule that marks where it starts unless it has no header.
     * The threads after its last group are followed by a blank line if the hunk is {@code followed} by anything in its
     * file. The rule stands in for git's {@code @@ -a,b +c,d @@}, whose
     * numbers the gutter shows already. Not the section heading git gives after them: it is a line from above the
     * hunk, which reads as one of its own. A header whose numbers cannot be read, whose lines are not numbered,
     * follows the rule as git printed it. The first hunk of a file, at {@code index} 0, has no rule, as the bar of the
     * file marks where it starts, unless its header has to be shown. When lines are left out before it, {@code gap},
     * a marker that says how many takes the place of the rule (ADR 77).
     */
    private static List<Pending> hunk(
            Hunk hunk,
            Vector<Row> rows,
            List<List<Integer>> groups,
            int index,
            boolean followed,
            Option<Folding.Gap> gap,
            boolean headed,
            Expansion.Fold fold,
            Layout layout) {
        boolean first = index == 0;
        Theme.SpanStyles styles = layout.styles();
        Span rule = rule(layout);
        // The lines of a hunk whose numbers cannot be read keep their prefix as git printed it, which a search
        // leaves out (ADR 126).
        int prefix = hunk.numbered() ? 0 : Rows.Source.prefix(hunk.header());
        // One for the hunk, which each of its lines holds: a long hunk has tens of thousands. The lines of a version
        // have none, so no range is started on them: a range is of the diff (ADR 102).
        Option<DiffStops.HunkAt> at =
                Option.when(layout.version().isEmpty(), () -> new DiffStops.HunkAt(layout.file(), index));
        List<Pending> header = !headed
                ? List.<Pending>empty()
                : gap.isDefined()
                        ? gap.toList().map(before -> marker(before, fold, layout))
                        : hunk.header().isEmpty() || (first && hunk.numbered())
                                ? List.empty()
                                : List.of(() -> List.of(Marked.plain(
                                        hunk.numbered()
                                                ? Line.from(rule)
                                                : Line.from(
                                                        rule,
                                                        styled(hunk.header(), Theme.Classes.DIFF_HUNK, styles)))));
        // No line of the diff is wider than the bar of its file, which is as wide as the file: each is measured at most
        // that wide without being drawn.
        Option<Integer> within = Option.some(layout.fileWidth());
        return header.appendAll(groups.zipWithIndex().<Pending>map(group -> () -> {
            List<Row> members = group._1().map(rows::get);
            List<Row> threaded = members.filter(row -> !row.remarks().threads().isEmpty());
            return group._1()
                    .map(order -> Tuple.of(rows.get(order), order))
                    .map(member -> new Marked(
                            () -> line(member._1(), layout),
                            Anchor.of(layout.path(), member._1().parsed().line())
                                    .map(on -> new Mark(
                                            on,
                                            1,
                                            layout.fileWidth(),
                                            at,
                                            Option.none(),
                                            0,
                                            leftOfContext(
                                                    layout.path(),
                                                    member._1().parsed().line()),
                                            member._2())),
                            List.empty(),
                            within,
                            () -> source(member._1(), prefix, layout)))
                    // The gutter of a line of a version shown blamed stays where it is when the code scrolls sideways
                    // (ADR 147).
                    .map(marked ->
                            layout.blame().isDefined() ? marked.keeping(layout.fileWidth() - layout.width()) : marked)
                    .appendAll(threaded.zipWithIndex()
                            .flatMap(row -> layout.blocks()
                                    .threads(
                                            row._1().remarks().threads(),
                                            Anchor.of(
                                                    layout.path(),
                                                    row._1().parsed().line()),
                                            followed
                                                    || group._2() < groups.size() - 1
                                                    || row._2() < threaded.size() - 1,
                                            styles)));
        }));
    }

    /**
     * The anchors {@code line} of a version rendered is placed by: its own, then for a context line the one by its
     * number in the old file, which {@code :} goes to in the original (ADR 140).
     */
    private static List<Anchor> placedBy(String path, DiffLine line) {
        return Anchor.of(path, line).<Anchor>map(anchor -> anchor).toList().appendAll(leftOfContext(path, line));
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

    /**
     * The lines still to be drawn of the {@code count} hunks of a file, as {@code hiding} hides them (ADR 144): each
     * hunk shown as {@code shown} draws it, and each run of hunks hidden with nothing shown between them as one marker,
     * {@code marker}, after the marker of the lines left out before the first of them, if there are some, as
     * {@code gap} draws it.
     */
    static List<Pending> hunks(
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
     * The marker of {@code run}, hunks of the file with the path {@code path} that filters hide, a stop of the cursor
     * (ADR 144): {@code rule}, how many lines they change, and the filters that hide them, or that they are shown, when
     * the user {@code opened} them, which Enter on it hides again; tinted {@code width} wide when the cursor is on it.
     */
    static Pending filteredMarker(
            List<HiddenHunks.Hidden> run, boolean opened, String path, Span rule, int width, Theme.SpanStyles styles) {
        String says = Messages.get(
                opened ? "content.filtered.opened" : "content.filtered.marker",
                run.map(HiddenHunks.Hidden::changes).sum().intValue(),
                String.join(", ", run.flatMap(HiddenHunks.Hidden::filters).distinct()));
        return () -> List.of(new Marked(
                Rows.kept(Line.from(rule, styled("┄ " + says + " ┄", Theme.Classes.DIFF_HUNK, styles))),
                Option.of(new Mark(
                        new Anchor.Filtered(path, run.map(HiddenHunks.Hidden::ranges), opened),
                        1,
                        width,
                        Option.none()))));
    }

    /**
     * That all the changes of a file are hidden, and by which filters, if they are (ADR 144).
     */
    static Option<String> allHidden(Filtering.Hiding hiding) {
        return Option.when(
                hiding.all(), () -> Messages.get("content.filtered.all", String.join(", ", hiding.filters())));
    }

    /**
     * The rule in the gutter that marks where a hunk starts.
     */
    private static Span rule(Layout layout) {
        return styled(
                "┄".repeat(2 * layout.digits() + 1) + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, layout.styles());
    }

    /**
     * The marker of {@code gap}, the lines a file leaves out, a stop of the cursor (ADR 77): the rule, and how many it
     * leaves out, or that more follow when that is not known, or that the file is being read whole, or could not be,
     * or is too long to be shown past where its read was cut, once the marker starts past it.
     */
    private static Pending marker(Folding.Gap gap, Expansion.Fold fold, Layout layout) {
        return marker(gap, fold, layout.path(), rule(layout), layout.fileWidth(), layout.styles());
    }

    /**
     * The marker of {@code gap} in the file with the path {@code path}, after {@code rule}, tinted {@code width} wide
     * when the cursor is on it.
     */
    static Pending marker(
            Folding.Gap gap, Expansion.Fold fold, String path, Span rule, int width, Theme.SpanStyles styles) {
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
        return () -> List.of(new Marked(
                Rows.kept(Line.from(rule, styled(says + " ┄", Theme.Classes.DIFF_HUNK, styles))),
                Option.of(new Mark(new Anchor.Gap(path, gap.from(), gap.until()), 1, width, Option.none()))));
    }

    /**
     * The gutter of {@code row}: the numbers of its line in the old and the new file, styled for an added or a removed
     * line when it is one, and where a review thread ends on it, or covers it, a mark in
     * {@link Theme.Classes#DIFF_THREAD} in place of the rule (ADR 55).
     */
    private static List<Span> gutter(Row row, Layout layout) {
        DiffLine line = row.parsed().line();
        LineRemarks remarks = row.remarks();
        Theme.SpanStyles styles = layout.styles();
        String numbers = numbers(line, layout);
        Style numbered = switch (line.kind()) {
            case ADDED -> styles.refined(Theme.Classes.DIFF_LINE_NUMBER, Theme.Classes.DIFF_ADDED_LINE_NUMBER);
            case REMOVED -> styles.refined(Theme.Classes.DIFF_LINE_NUMBER, Theme.Classes.DIFF_REMOVED_LINE_NUMBER);
            case CONTEXT, NOTE -> styles.of(Theme.Classes.DIFF_LINE_NUMBER);
        };
        return blamed(line, layout)
                .appendAll(
                        !remarks.threads().isEmpty() || remarks.covered()
                                ? List.of(
                                        Span.styled(numbers + " ", numbered),
                                        styled(
                                                remarks.threads().isEmpty() ? "┃" : "●",
                                                Theme.Classes.DIFF_THREAD,
                                                styles),
                                        Span.styled(" ", numbered))
                                : List.of(Span.styled(numbers + GUTTER_END, numbered)));
    }

    /**
     * The column of the blame beside {@code line}, if the version shown is blamed (ADR 147).
     */
    private static List<Span> blamed(DiffLine line, Layout layout) {
        return layout.blame()
                .map(column -> column.beside(layout.version().flatMap(version -> version.number(line))))
                .getOrElse(List.empty());
    }

    /**
     * The numbers of {@code line} in its gutter: in the old and the new file, or in the version shown (ADR 102), in one
     * column when it is shown blamed (ADR 147).
     */
    private static String numbers(DiffLine line, Layout layout) {
        return layout.version()
                .map(version -> layout.blame().isDefined()
                        ? number(version.number(line), layout.digits())
                        : versionNumber(version, line, layout.digits()))
                .getOrElse(() ->
                        number(line.oldNumber(), layout.digits()) + " " + number(line.newNumber(), layout.digits()));
    }

    /**
     * What the line of {@code row} is searched in (ADR 126): its text, without the first {@code prefix} characters
     * of a line kept as git printed it, after its gutter and its marker, which a note has none of. Its gutter is as
     * long whatever marks are in it ({@link #gutter}), after the column of its blame, if any, which is not searched
     * (ADR 147).
     */
    private static Rows.Source source(Row row, int prefix, Layout layout) {
        DiffLine line = row.parsed().line();
        int marker = line.kind() == DiffLine.Kind.NOTE ? 0 : 1 + MARGIN.length();
        Rows.Source text = Rows.Source.cut(line.text(), prefix);
        return new Rows.Source(
                text.text(),
                BlameColumn.length(blamed(line, layout))
                        + numbers(line, layout).length()
                        + GUTTER_END.length()
                        + marker
                        + text.from());
    }

    /**
     * The line of {@code row} after its gutter, with its segments, and its tokens if it was lexed and the
     * {@code layout} colours code. Changed lines are padded to the width of the layout, so their background makes a
     * block, which the area draws as far as its edge (ADR 80).
     */
    private static Line line(Row row, Layout layout) {
        DiffLine line = row.parsed().line();
        List<Span> gutter = gutter(row, layout);
        Option<Array<Token>> tokens = layout.syntax() ? row.parsed().tokens() : Option.none();
        return switch (line.kind()) {
            case CONTEXT ->
                tokens.map(lexed -> Line.from(Iterator.concat(
                                        gutter,
                                        List.of(Span.raw(" " + MARGIN)),
                                        pieces(row.parsed().words(), lexed)
                                                .iterator()
                                                .map(piece -> context(piece, layout.styles())))
                                .toJavaList()))
                        .getOrElse(() ->
                                Line.from(Iterator.concat(gutter, List.of(Span.raw(" " + MARGIN + line.text())))
                                        .toJavaList()));
            case NOTE ->
                Line.from(Iterator.concat(gutter, List.of(styled(line.text(), Theme.Classes.DIM, layout.styles())))
                        .toJavaList());
            case ADDED -> changed(row, gutter, tokens, Change.ADDED, layout);
            case REMOVED -> changed(row, gutter, tokens, Change.REMOVED, layout);
        };
    }

    /**
     * The changed line of {@code row}: its marker and its unchanged parts in the line class of {@code change}, its
     * changed words in its word class if the {@code layout} highlights them, padded to the width of the {@code layout}. Its code is coloured by its
     * {@code tokens} if it was lexed, unless the line class gives it no background: then its colour is what tells it
     * is changed, and a line of tokens only would look like context. In a version of the file, its marker is blank
     * when the line class gives it a background, which tells it is changed already (ADR 102).
     */
    private static Line changed(Row row, List<Span> gutter, Option<Array<Token>> tokens, Change change, Layout layout) {
        Theme.SpanStyles styles = layout.styles();
        boolean tinted = styles.of(change.lineClass()).bg().isPresent();
        Option<Array<Token>> coloured = tokens.filter(ignored -> tinted);
        String marker = tinted && layout.version().isDefined() ? " " : change.marker();
        List<Piece> pieces = pieces(row.parsed().words(), coloured.getOrElse(Array.empty()));
        // Each piece is drawn as a span by itself, and pieces cut from a grapheme, like an emoji from its variation
        // selector, are narrower together than their text: only a line of one piece is as wide as it was measured.
        int used = 1
                + MARGIN.length()
                + (pieces.size() == 1
                        ? row.measured()
                        : pieces.map(piece -> width(piece.text())).sum().intValue());
        return Line.from(Iterator.concat(
                        gutter,
                        List.of(styled(marker + MARGIN, change.lineClass(), styles)),
                        pieces.iterator().map(piece -> {
                            String cssClass =
                                    piece.changed() && layout.words() ? change.wordClass() : change.lineClass();
                            return coloured.isDefined()
                                    ? changedCode(piece, cssClass, styles)
                                    : styled(piece.text(), cssClass, styles);
                        }),
                        List.of(styled(" ".repeat(Math.max(0, layout.width() - used)), change.lineClass(), styles)))
                .toJavaList());
    }

    /**
     * A piece of a context line: in the colour of its token, else plain.
     */
    static Span context(Piece piece, Theme.SpanStyles styles) {
        return piece.kind()
                .map(kind -> styled(piece.text(), syntaxClass(kind), styles))
                .getOrElse(() -> Span.raw(piece.text()));
    }

    /**
     * A piece of a changed line in {@code diffClass}: with its background, its attributes, and the colour of its
     * token, or that of {@link Theme.Classes#SYNTAX_TEXT} outside a token, which is the plain colour unless the theme
     * gives it one.
     */
    static Span changedCode(Piece piece, String diffClass, Theme.SpanStyles styles) {
        return Span.styled(
                piece.text(),
                styles.code(diffClass, piece.kind().map(RichDiff::syntaxClass).getOrElse(Theme.Classes.SYNTAX_TEXT)));
    }

    /**
     * The text of a line in pieces that are each part of one of its {@code segments} and of one of its
     * {@code tokens} or none: a piece is cut where a segment or a token starts or ends. Both are in order, so the
     * segment and the token of each piece are found by going on from those of the piece before it: a minified file
     * has a line of thousands of tokens.
     */
    static List<Piece> pieces(List<Segment> segments, Seq<Token> tokens) {
        if (tokens.isEmpty()) {
            return segments.map(segment -> new Piece(segment.text(), segment.changed(), Option.none()));
        }
        String text = segments.map(Segment::text).mkString();
        Vector<Integer> ends = segments.scanLeft(
                        0, (end, segment) -> end + segment.text().length())
                .tail()
                .toVector();
        List<Integer> cuts = ends.toList()
                .prepend(0)
                .appendAll(tokens.flatMap(token -> List.of(token.start(), token.end())))
                .filter(cut -> cut <= text.length())
                .sorted()
                .distinct();
        Cutter cutter = new Cutter(text, segments.toVector(), ends, tokens.toVector());
        return cuts.zip(cuts.tail())
                .foldLeft(new Cursor(List.empty(), 0, 0), cutter::next)
                .pieces()
                .reverse();
    }

    static String syntaxClass(Token.Kind kind) {
        return switch (kind) {
            case KEYWORD -> Theme.Classes.SYNTAX_KEYWORD;
            case STRING -> Theme.Classes.SYNTAX_STRING;
            case COMMENT -> Theme.Classes.SYNTAX_COMMENT;
            case NUMBER -> Theme.Classes.SYNTAX_NUMBER;
            case ANNOTATION -> Theme.Classes.SYNTAX_ANNOTATION;
        };
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

    static int width(String text) {
        return Span.raw(text).width();
    }

    static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
        return Span.styled(text, styles.of(cssClass));
    }

    /**
     * A piece of the text of a line: whether it is part of a changed word, and the kind of token it is part of.
     */
    record Piece(String text, boolean changed, Option<Token.Kind> kind) {}

    /**
     * Lines still to be drawn: what a file is cut into once it is measured, so that its parts are drawn side by side.
     */
    @FunctionalInterface
    interface Pending {

        List<Marked> draw();
    }

    /**
     * What draws a line, the stops of the cursor that start on it, if any do, and how wide it is at most, when that is
     * known without drawing it: a line of a diff is no wider than its file, so a long diff is measured without drawing
     * each of its lines only to throw it away. With what it is searched in (ADR 126): the code of a line of a diff,
     * without drawing it; all the text of any other line, drawn already. A row of the split view has a stop for each
     * of its halves that holds a line, and where its halves are (ADR 139); a row of a file drawn split that is as wide
     * as the area, as its bar, is pinned: it does not scroll sideways, as the halves scroll each on their own. A line
     * of a version shown blamed keeps its gutter, the column of its blame and its numbers, where it is (ADR 147).
     *
     * @param kept how many columns at the start of the line stay where they are when the area scrolls sideways: none,
     *             {@link Drawn#PINNED} for all of them
     */
    record Marked(
            Supplier<Line> line,
            List<Mark> marks,
            List<Anchor> places,
            Option<Integer> width,
            Supplier<Rows.Source> source,
            Option<Halves> halves,
            int kept) {

        Marked(
                Supplier<Line> line,
                List<Mark> marks,
                List<Anchor> places,
                Option<Integer> width,
                Supplier<Rows.Source> source,
                Option<Halves> halves) {
            this(line, marks, places, width, source, halves, 0);
        }

        Marked(Supplier<Line> line, Option<Mark> mark) {
            this(line, mark, List.empty());
        }

        Marked(Supplier<Line> line, Option<Mark> mark, List<Anchor> places) {
            this(line, mark, places, Option.none(), () -> Rows.Source.of(line.get()));
        }

        Marked(
                Supplier<Line> line,
                Option<Mark> mark,
                List<Anchor> places,
                Option<Integer> width,
                Supplier<Rows.Source> source) {
            this(line, mark.toList(), places, width, source, Option.none());
        }

        /**
         * How wide the line is, at most: drawn to be measured when that is not known.
         */
        int measured() {
            return width.getOrElse(() -> line.get().width());
        }

        /**
         * This line changed by {@code change} when it is drawn.
         */
        Marked mapped(UnaryOperator<Line> change) {
            Supplier<Line> drawn = line;
            Supplier<Line> changed = () -> change.apply(drawn.get());
            return new Marked(changed, marks, places, Option.none(), () -> Rows.Source.of(changed.get()), halves, kept);
        }

        /**
         * This line with its stops on {@code side} (ADR 139).
         */
        Marked onSide(ReviewThread.Side side) {
            return new Marked(line, marks.map(mark -> mark.onSide(side)), places, width, source, halves, kept);
        }

        /**
         * This line pinned, when it has no halves: a row of a file drawn split, as wide as the area (ADR 139).
         */
        Marked pin() {
            return new Marked(line, marks, places, width, source, halves, halves.isEmpty() ? Drawn.PINNED : 0);
        }

        /**
         * This line with its first {@code columns} columns kept where they are when the area scrolls sideways.
         */
        Marked keeping(int columns) {
            return new Marked(line, marks, places, width, source, halves, columns);
        }

        static Marked plain(Line line) {
            return new Marked(Rows.kept(line), Option.none());
        }
    }

    /**
     * A stop of the cursor (ADR 60): what it is, how many lines it has itself, after which the lines that are no stop
     * are part of it too, and how many columns the cursor is tinted on.
     *
     * @param anchor what it is
     * @param lines  how many lines it has itself
     * @param width  how many columns its tint covers: the width of its file, or of its half in the split view
     * @param hunk   the hunk of a line, which a range stays in; none for what is not a line
     * @param side   the side of the split view it is on, the half it is in (ADR 139); none for what is as wide as the
     *               area, and in the unified view
     * @param column the column of its line its tint starts at: that of its half in the split view
     * @param also   the other anchor of a context line, which the stop is found by too: on the left side, by its number
     *               in the old file, in the unified view, where the line is a stop on the right side only (ADR 139)
     * @param order  the index of its line among the lines of its hunk, in the order of the diff; -1 for what is not a
     *               line
     */
    public record Mark(
            Anchor anchor,
            int lines,
            int width,
            Option<DiffStops.HunkAt> hunk,
            Option<ReviewThread.Side> side,
            int column,
            Option<Anchor> also,
            int order) {

        public Mark(Anchor anchor, int lines, int width, Option<DiffStops.HunkAt> hunk) {
            this(anchor, lines, width, hunk, Option.none(), 0, Option.none(), -1);
        }

        /**
         * Whether the stop is {@code anchor}, or a context line it is the other anchor of.
         */
        public boolean is(Anchor anchor) {
            return this.anchor.equals(anchor) || also.contains(anchor);
        }

        /**
         * This stop in the half of {@code side} (ADR 139).
         */
        Mark onSide(ReviewThread.Side side) {
            return new Mark(anchor, lines, width, hunk, Option.some(side), column, also, order);
        }
    }

    /**
     * Where a stop of the cursor starts.
     *
     * @param mark the stop
     * @param row  the line it starts on
     */
    public record Stop(Mark mark, int row) {}

    /**
     * Where a line of a version rendered is drawn (ADR 103): the first row of the block it is part of. No stop of the
     * cursor, which a version rendered has none of but the bar of its file.
     *
     * @param anchor the line
     * @param row    the first row of its block
     */
    public record Place(Anchor anchor, int row) {}

    /**
     * The lines of a diff, each drawn when it shows ({@link Rows}), where each stop of the cursor in them starts, in
     * order, and what works out how wide the widest of them is.
     *
     * @param widest  how wide the widest line is, worked out when it is asked for: the lines of a diff are not drawn
     *                for it, as each is no wider than its file ({@link Marked#width}); other lines are drawn to be
     *                measured, as {@link Rows#widest} does. In the split view, as wide as the area and as far as its
     *                halves scroll sideways
     * @param halves  where the halves of each row of the split view are, by the index of the row; none for a row as
     *                wide as the area, and none at all in the unified view (ADR 139)
     * @param kept    how many columns at the start of each row stay where they are when the area scrolls sideways, by
     *                the index of the row: all of those of a row of the split view as wide as the area of a file drawn
     *                split, as a line of a file the split view draws unified scrolls as in the unified view (ADR 139);
     *                the gutter of a line of a version shown blamed (ADR 147). None at all when no row keeps any
     * @param divider the column of the area the rule between the halves of the split view is at
     * @param note    what the title of the area says of how the diff is drawn: that the area is too narrow for the
     *                split view
     */
    public record Drawn(
            Rows rows,
            List<Stop> stops,
            List<Place> places,
            IntSupplier widest,
            Vector<Option<Halves>> halves,
            Vector<Integer> kept,
            Option<Integer> divider,
            Option<String> note) {

        /**
         * The columns {@link #keptAt} says a row keeps that does not scroll sideways at all, as wide as the area.
         */
        public static final int PINNED = Integer.MAX_VALUE;

        Drawn(Rows rows, List<Stop> stops) {
            this(rows, stops, List.empty(), rows::widest, Vector.empty(), Vector.empty(), Option.none(), Option.none());
        }

        private static Drawn of(List<Marked> marked) {
            return new Drawn(
                    Rows.drawn(marked.map(Marked::line), marked.map(Marked::source)),
                    marked.zipWithIndex().flatMap(line -> line._1().marks().map(mark -> new Stop(mark, line._2()))),
                    marked.zipWithIndex()
                            .flatMap(line -> line._1().places().map(anchor -> new Place(anchor, line._2()))),
                    // On the threads of the common pool, as Rows.widest measures, for the lines that are drawn for it.
                    () -> marked.toJavaParallelStream()
                            .mapToInt(Marked::measured)
                            .max()
                            .orElse(0),
                    marked.exists(line -> line.halves().isDefined())
                            ? marked.map(Marked::halves).toVector()
                            : Vector.empty(),
                    marked.exists(line -> line.kept() > 0)
                            ? marked.map(Marked::kept).toVector()
                            : Vector.empty(),
                    Option.none(),
                    Option.none());
        }

        /**
         * {@code lines}, without a stop: what has no cursor.
         */
        public static Drawn plain(List<Line> lines) {
            return plain(Rows.of(lines));
        }

        /**
         * {@code rows}, without a stop: what has no cursor.
         */
        public static Drawn plain(Rows rows) {
            return new Drawn(rows, List.empty());
        }

        /**
         * All the lines, drawn.
         */
        public List<Line> lines() {
            return rows.all();
        }

        /**
         * Whether the diff is drawn with the old and the new file side by side (ADR 139).
         */
        public boolean split() {
            return divider.isDefined();
        }

        /**
         * Where the halves of row {@code row} of the split view are; none for a row as wide as the area.
         */
        public Option<Halves> halvesOf(int row) {
            return row >= 0 && row < halves.size() ? halves.get(row) : Option.none();
        }

        /**
         * How many columns at the start of row {@code row} stay where they are when the area scrolls sideways: all of
         * those of a row of the split view as wide as the area (ADR 139), the gutter of a line of a version shown
         * blamed (ADR 147), else none.
         */
        public int keptAt(int row) {
            return row >= 0 && row < kept.size() ? kept.get(row) : 0;
        }

        /**
         * These lines after {@code before}, their stops moved down by as many lines.
         */
        public Drawn after(List<Line> before) {
            IntSupplier inBody = widest;
            return new Drawn(
                    rows.after(Rows.of(before)),
                    stops.map(stop -> new Stop(stop.mark(), stop.row() + before.size())),
                    places.map(place -> new Place(place.anchor(), place.row() + before.size())),
                    () -> Math.max(
                            inBody.getAsInt(), before.map(Line::width).max().getOrElse(0)),
                    halves.isEmpty()
                            ? halves
                            : Vector.<Option<Halves>>fill(before.size(), Option::none)
                                    .appendAll(halves),
                    kept.isEmpty() ? kept : Vector.fill(before.size(), () -> 0).appendAll(kept),
                    divider,
                    note);
        }

        /**
         * These lines with what comes before their first stop as a stop of its own, the lead, if anything does.
         */
        public Drawn withLead() {
            return stops.headOption()
                    .filter(first -> first.row() > 0)
                    .map(first -> new Drawn(
                            rows,
                            stops.prepend(new Stop(new Mark(new Anchor.Lead(), first.row(), 0, Option.none()), 0)),
                            places,
                            widest,
                            halves,
                            kept,
                            divider,
                            note))
                    .getOrElse(this);
        }

        /**
         * These lines with the rule between the halves of the split view at column {@code column} of the area.
         */
        Drawn withDivider(int column) {
            return new Drawn(rows, stops, places, widest, halves, kept, Option.some(column), note);
        }

        /**
         * These lines with {@code says} in the title of the area, after what it says already.
         */
        Drawn withNote(String says) {
            return new Drawn(
                    rows,
                    stops,
                    places,
                    widest,
                    halves,
                    kept,
                    divider,
                    Option.some(note.map(before -> Messages.get("content.notes", before, says))
                            .getOrElse(says)));
        }
    }

    /**
     * What drawing each line of a file takes from the file: how many {@code digits} its numbers have, how many
     * columns after the gutter its changed lines are padded to, its {@code width}, how the threads on its lines are
     * drawn, whether its code is coloured by its syntax, whether the changed words of a line are highlighted, the
     * styles of the theme, the version of the file shown in place of its diff, if one is (ADR 102), and the column of
     * its blame, if it is blamed (ADR 147).
     */
    private record Layout(
            String path,
            int file,
            int digits,
            int width,
            int fileWidth,
            Blocks blocks,
            boolean syntax,
            boolean words,
            Theme.SpanStyles styles,
            Option<FileVersion> version,
            Option<BlameColumn> blame) {}

    /**
     * A line of a file with what drawing it takes besides the layout of its file: how many columns its text was
     * {@code measured} at, and the review threads on it.
     */
    private record Row(ParsedLine parsed, int measured, LineRemarks remarks) {}

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
     * How the review threads of a file are drawn: as blocks that start {@code indent} columns in, two after the
     * gutter, and are {@code width} columns wide, as {@code style} says.
     */
    record Blocks(int indent, int width, RemarkLines.RemarkStyle style) {

        /**
         * The lines of {@code threads}, after {@code line} if they are, each a stop of the cursor, then a blank line
         * that sets them apart from what follows them in their file, if they are {@code followed} by anything.
         */
        List<Marked> threads(
                List<ReviewThread> threads, Option<Anchor.OnLine> line, boolean followed, Theme.SpanStyles styles) {
            return marked(threads, line, RemarkLines.blocks(threads, indent, width, style, styles))
                    .appendAll(followed && !threads.isEmpty() ? List.of(Marked.plain(Line.from(""))) : List.empty());
        }

        /**
         * The lines of {@code threads}, which cannot be placed, under a line that says so, each a stop of the cursor.
         */
        List<Marked> unplaced(List<ReviewThread> threads, Theme.SpanStyles styles) {
            return threads.isEmpty()
                    ? List.empty()
                    : marked(threads, Option.none(), RemarkLines.unplacedBlocks(threads, indent, width, style, styles))
                            .prepend(Marked.plain(RemarkLines.unplacedHeading(indent, styles)));
        }

        /**
         * The {@code blocks} of {@code threads}, with a blank line between two of them, the first line of each of
         * their parts the start of its stop, as {@link DiffStops#anchors} gives them (ADR 62).
         */
        private List<Marked> marked(
                List<ReviewThread> threads, Option<Anchor.OnLine> line, List<List<List<Line>>> blocks) {
            return threads.zip(blocks)
                    .map(block -> DiffStops.anchors(block._1(), line, style.collapsed())
                            .zip(block._2())
                            .flatMap(part -> part._2()
                                    .zipWithIndex()
                                    .map(drawn -> new Marked(
                                            Rows.kept(drawn._1()),
                                            Option.when(
                                                    drawn._2() == 0,
                                                    () -> new Mark(
                                                            part._1(),
                                                            part._2().size(),
                                                            indent + width,
                                                            Option.none()))))))
                    .intersperse(List.of(Marked.plain(Line.from(""))))
                    .flatMap(Function.identity());
        }
    }

    /**
     * How far cutting a line in pieces got: the pieces cut, the last first, and the index of the segment and of the
     * token the last is in, or after.
     */
    private record Cursor(List<Piece> pieces, int segment, int token) {}

    /**
     * A line cut in pieces: its text, its segments, where each of those ends, and its tokens.
     */
    private record Cutter(String text, Vector<Segment> segments, Vector<Integer> ends, Vector<Token> tokens) {

        /**
         * {@code at} with the piece from {@code cut._1} to {@code cut._2} cut too: its segment and its token are found
         * by going on from those of the piece before it.
         */
        Cursor next(Cursor at, Tuple2<Integer, Integer> cut) {
            int segment = Iterator.iterate(at.segment(), i -> i + 1)
                    .find(i -> ends.get(i) > cut._1)
                    .get();
            int token = Iterator.iterate(at.token(), i -> i + 1)
                    .find(i -> i >= tokens.size() || tokens.get(i).end() > cut._1)
                    .get();
            Option<Token.Kind> kind = Option.when(
                            token < tokens.size() && tokens.get(token).start() <= cut._1, () -> tokens.get(token))
                    .map(Token::kind);
            Piece piece = new Piece(
                    text.substring(cut._1, cut._2), segments.get(segment).changed(), kind);
            return new Cursor(at.pieces().prepend(piece), segment, token);
        }
    }
}
