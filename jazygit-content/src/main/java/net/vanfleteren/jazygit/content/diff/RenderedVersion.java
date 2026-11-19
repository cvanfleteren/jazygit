package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.content.diff.Drawn.Mark;
import net.vanfleteren.jazygit.content.diff.RichDiff.Change;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Rendered;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * A version of a file rendered in place of its diff (ADR 103): Markdown as prose, JSON and YAML as a tree (ADR 122,
 * 123), with a bar in the gutter beside what changed.
 */
final class RenderedVersion {

    /**
     * The gutter of a version rendered: the bar beside a block that changed, and a space (ADR 103).
     */
    static final int GUTTER = 2;

    private static final String BAR = "▎";

    private RenderedVersion() {}

    /**
     * {@code version} of {@code parsed}, a file read whole as {@code fold} says, rendered {@code width} columns wide,
     * its gutter included (ADR 103): its bar, a stop of the cursor whose lines the rows after it are, then each of its
     * blocks, with a bar in the gutter beside one that holds a line that changed, the lines each was drawn from placed
     * on its first row, and a line that says so when the file was read up to a cut.
     */
    static List<Marked> of(
            ParsedFile parsed, FileVersion version, Expansion.Fold fold, int width, Theme.SpanStyles styles) {
        String path = parsed.file().path();
        Vector<DiffLine> lines = parsed.hunks()
                .flatMap(ParsedHunk::lines)
                .map(ParsedLine::line)
                .filter(line -> line.kind() != DiffLine.Kind.NOTE)
                .toVector();
        Change change = version == FileVersion.NEW ? Change.ADDED : Change.REMOVED;
        // A theme that tints a changed line has a bar in the colour of its marker, green for an added one as GitHub's
        // is: the tint itself is a background, too dark to be seen as a bar. One that does not tint, as mono, has the
        // marker the line would have.
        Span changed = RichDiff.styled(
                styles.of(change.lineClass()).bg().isPresent() ? BAR : change.marker(), change.markerClass(), styles);
        Span unchanged = Span.raw(" ");
        String bar = FileSections.bar(parsed, version);
        int tinted = Math.max(RichDiff.width(bar), width);
        Marked header = FileSections.barLine(bar, path, tinted, styles);
        Option<Rendered> tree = Rendered.of(path).filter(Rendered::tree);
        if (tree.isDefined()) {
            return tree(tree.get(), new RenderedFile(path, lines, fold, width, tinted, styles), changed, unchanged)
                    .prepend(header);
        }
        Vector<RenderedMarkdown.Block> blocks = RenderedMarkdown.blocks(
                        lines.map(DiffLine::text), Math.max(1, width - GUTTER), styles)
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
                    .map(row -> Marked.of(
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
                                () -> Marked.plain(Line.from(RichDiff.styled(
                                        Messages.get("content.file.renderedCut"), Theme.Classes.DIM, styles))))
                        .toList());
    }

    /**
     * The rows of a version of JSON or YAML rendered, a file of {@code kind}, its source {@code lines}, read as
     * {@code fold} says, {@code width} columns wide, its gutter included (ADR 122, 123): each a stop of the cursor, but
     * the keys of a table, with a bar in the gutter when a line it stands for changed, those lines placed on it. A file
     * that cannot be read is its source, after a line that says why, its lines placed on them, and no stops but the bar
     * of the file.
     */
    private static List<Marked> tree(Rendered kind, RenderedFile file, Span changed, Span unchanged) {
        String path = file.path();
        Vector<DiffLine> lines = file.lines();
        Expansion.Fold fold = file.fold();
        int width = file.width();
        int tinted = file.tinted();
        Theme.SpanStyles styles = file.styles();
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
                        Math.max(1, width - GUTTER),
                        fold.nodes(),
                        styles)
                .fold(
                        why -> {
                            LexedHighlighter lexed = new LexedHighlighter(styles);
                            List<Marked> source = lines.zipWithIndex()
                                    .<Marked>map(at -> Marked.of(
                                            Rows.kept(Line.from(List.of(
                                                            gutter.apply(List.of(at._2() + 1)), Span.raw(" "))
                                                    .appendAll(List.ofAll(lexed.highlight(
                                                                    at._1().text(), kind.language(), Style.EMPTY, null))
                                                            .flatMap(drawn -> drawn.spans()))
                                                    .toJavaList())),
                                            Option.none(),
                                            placed.apply(List.of(at._2() + 1))))
                                    .toList();
                            return source.prepend(
                                    Marked.plain(Line.from(RichDiff.styled(why, Theme.Classes.DIM, styles))));
                        },
                        rows -> rows.map(row -> Marked.of(
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
     * The anchors {@code line} of a version rendered is placed by: its own, then for a context line the one by its
     * number in the old file, which {@code :} goes to in the original (ADR 140).
     */
    private static List<Anchor> placedBy(String path, DiffLine line) {
        return Anchor.of(path, line)
                .<Anchor>map(anchor -> anchor)
                .toList()
                .appendAll(RichDiff.leftOfContext(path, line));
    }

    /**
     * A version of a file rendered: its {@code path}, its source {@code lines}, read as {@code fold} says,
     * {@code width} columns wide, its gutter included, its bar tinted {@code tinted} wide, in the {@code styles} of
     * the theme (ADR 103).
     */
    private record RenderedFile(
            String path, Vector<DiffLine> lines, Expansion.Fold fold, int width, int tinted, Theme.SpanStyles styles) {}
}
