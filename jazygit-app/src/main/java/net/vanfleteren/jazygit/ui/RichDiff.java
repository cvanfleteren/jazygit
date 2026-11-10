package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Folding;
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
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.DiffStops;
import net.vanfleteren.jazygit.state.Expansion;
import net.vanfleteren.jazygit.state.PlacedRemarks;
import net.vanfleteren.jazygit.state.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.PlacedRemarks.LineRemarks;
import net.vanfleteren.jazygit.syntax.Token;

/**
 * A unified diff drawn rich (ADR 44): what comes before its first file as it is, then each file under a bar that
 * says what happened to it, its lines numbered in the old and the new file, and the words that changed in a changed
 * line highlighted.
 */
final class RichDiff {

    private static final String GUTTER_END = " │ ";
    private static final LineRemarks NO_REMARKS = new LineRemarks(List.empty(), false);
    private static final Pending BLANK = () -> List.of(Marked.plain(Line.from("")));

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
            ParsedDiff diff,
            boolean syntax,
            boolean words,
            Option<ContentView.ShownRemarks> remarks,
            Theme.SpanStyles styles) {
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
            Option<ContentView.ShownRemarks> remarks,
            Option<DiffStops.HunkAt> asGiven,
            Theme.SpanStyles styles) {
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
                                asGiven.filter(hunk -> hunk.file() == file._2()).map(DiffStops.HunkAt::hunk),
                                syntax,
                                words,
                                style,
                                styles))
                .intersperse(List.of(BLANK))
                .flatMap(file -> file);
        return Drawn.of(parsed.preamble()
                .map(line -> new Marked(() -> Line.from(Span.raw(line)), Option.none()))
                .appendAll(inParallel(pending, Pending::draw).flatMap(lines -> lines)));
    }

    /**
     * What {@code draw} gives for each of {@code items}, in their order, each worked out on a thread of the common
     * pool. Drawing is pure, and the render thread waits for it, so nothing changes the theme while it runs.
     */
    private static <T, R> List<R> inParallel(List<T> items, Function<T, R> draw) {
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
     * pairs where they can be (ADR 83).
     */
    private static List<Pending> file(
            ParsedFile parsed,
            Option<FileRemarks> remarks,
            int index,
            Expansion.Fold fold,
            Option<Integer> asGiven,
            boolean syntax,
            boolean words,
            RemarkLines.RemarkStyle style,
            Theme.SpanStyles styles) {
        FileDiff file = parsed.file();
        Option<FileVersion> version = fold.version();
        Vector<ParsedLine> lines = parsed.hunks().flatMap(ParsedHunk::lines).toVector();
        int digits = lines.flatMap(line ->
                        line.line().oldNumber().toList().appendAll(line.line().newNumber()))
                .max()
                .map(number -> String.valueOf(number).length())
                .getOrElse(1);
        // A version keeps both columns, so its code does not move sideways from the diff's (ADR 102).
        int gutter = 2 * digits + 1 + GUTTER_END.length();
        String bar = version.map(shown -> bar(file, shown)).getOrElse(() -> bar(file));
        // Each line is measured once, here: its width also tells how far a changed line is padded.
        Vector<Integer> measured =
                inParallel(lines.toList(), line -> width(line.line().text())).toVector();
        int width = Math.max(
                width(bar), gutter + measured.max().map(text -> text + 1).getOrElse(0));
        Blocks blocks = new Blocks(gutter + 2, RemarkLines.blockWidth(width), style);
        Layout layout =
                new Layout(file.path(), index, digits, width - gutter, width, blocks, syntax, words, styles, version);
        Vector<LineRemarks> onLines = remarks.map(
                        placed -> placed.lines().flatMap(hunk -> hunk).toVector())
                .getOrElse(() -> Vector.fill(lines.size(), () -> NO_REMARKS));
        // A version leaves no line out; the threads on the other version's lines are not shown with it.
        List<Folding.Gap> gaps = version.isDefined() ? List.empty() : Folding.gaps(file, fold.length());
        Option<Folding.Gap> trailing =
                gaps.find(gap -> gap.before() == parsed.hunks().size());
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
                                .map(text -> Marked.plain(Line.from(styled(text, Theme.Classes.DIM, styles))))))
                .appendAll(remarks.toList()
                        .<Pending>map(placed -> () -> blocks.threads(
                                placed.onFile(), Option.none(), !file.hunks().isEmpty() || unplaced, styles)))
                .appendAll(parsed.hunks()
                        .zipWithIndex((hunk, at) -> hunk(
                                hunk.hunk(),
                                byHunk.get(at),
                                groups.get(at),
                                at,
                                afterLast || at < parsed.hunks().size() - 1,
                                gaps.find(gap -> gap.before() == at),
                                fold,
                                layout))
                        .flatMap(hunk -> hunk))
                .appendAll(trailing.toList().map(gap -> marker(gap, fold, layout)))
                .appendAll(cut.toList()
                        .<Pending>map(text ->
                                () -> List.of(Marked.plain(Line.from(styled(text, Theme.Classes.DIM, styles))))))
                .appendAll(remarks.filter(ignored -> version.isEmpty())
                        .toList()
                        .<Pending>map(placed -> () -> blocks.unplaced(placed.unplaced(), styles)));
    }

    /**
     * What the bar of {@code file} says while {@code version} of it is shown in place of its diff (ADR 102): which
     * version, and its path in it, the one it had before a rename for the original.
     */
    private static String bar(FileDiff file, FileVersion version) {
        String path = version == FileVersion.ORIGINAL ? file.origin().getOrElse(file.path()) : file.path();
        String what = Messages.get(version == FileVersion.NEW ? "content.file.newVersion" : "content.file.original");
        return " " + what + "  " + path + " ";
    }

    /**
     * What the bar of {@code file} says: what happened to it and its path, and how its mode changed if it did.
     */
    private static String bar(FileDiff file) {
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
            Expansion.Fold fold,
            Layout layout) {
        boolean first = index == 0;
        Theme.SpanStyles styles = layout.styles();
        Span rule = rule(layout);
        // One for the hunk, which each of its lines holds: a long hunk has tens of thousands. The lines of a version
        // have none, so no range is started on them: a range is of the diff (ADR 102).
        Option<DiffStops.HunkAt> at =
                Option.when(layout.version().isEmpty(), () -> new DiffStops.HunkAt(layout.file(), index));
        List<Pending> header = gap.isDefined()
                ? gap.toList().map(before -> marker(before, fold, layout))
                : hunk.header().isEmpty() || (first && hunk.numbered())
                        ? List.empty()
                        : List.of(() -> List.of(Marked.plain(
                                hunk.numbered()
                                        ? Line.from(rule)
                                        : Line.from(rule, styled(hunk.header(), Theme.Classes.DIFF_HUNK, styles)))));
        return header.appendAll(groups.zipWithIndex().<Pending>map(group -> () -> {
            List<Row> members = group._1().map(rows::get);
            List<Row> threaded = members.filter(row -> !row.remarks().threads().isEmpty());
            return members.map(row -> new Marked(
                            () -> line(row, layout),
                            Anchor.of(layout.path(), row.parsed().line())
                                    .map(on -> new Mark(on, 1, layout.fileWidth(), at))))
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
                Rows.kept(Line.from(rule(layout), styled(says + " ┄", Theme.Classes.DIFF_HUNK, layout.styles()))),
                Option.of(new Mark(
                        new Anchor.Gap(layout.path(), gap.from(), gap.until()),
                        1,
                        layout.fileWidth(),
                        Option.none()))));
    }

    /**
     * The gutter of {@code row}: the numbers of its line in the old and the new file, and where a review thread ends
     * on it, or covers it, a mark in {@link Theme.Classes#DIFF_THREAD} in place of the rule (ADR 55).
     */
    private static List<Span> gutter(Row row, Layout layout) {
        DiffLine line = row.parsed().line();
        LineRemarks remarks = row.remarks();
        Theme.SpanStyles styles = layout.styles();
        // A version shows its numbers in its own column of the diff's, the other one blank (ADR 102).
        Option<Integer> old =
                layout.version().map(version -> version == FileVersion.ORIGINAL).getOrElse(true)
                        ? line.oldNumber()
                        : Option.none();
        Option<Integer> neu =
                layout.version().map(version -> version == FileVersion.NEW).getOrElse(true)
                        ? line.newNumber()
                        : Option.none();
        String numbers = number(old, layout.digits()) + " " + number(neu, layout.digits());
        return !remarks.threads().isEmpty() || remarks.covered()
                ? List.of(
                        styled(numbers + " ", Theme.Classes.DIFF_LINE_NUMBER, styles),
                        styled(remarks.threads().isEmpty() ? "┃" : "●", Theme.Classes.DIFF_THREAD, styles),
                        styled(" ", Theme.Classes.DIFF_LINE_NUMBER, styles))
                : List.of(styled(numbers + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, styles));
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
                                        List.of(Span.raw(" ")),
                                        pieces(row.parsed().words(), lexed)
                                                .iterator()
                                                .map(piece -> context(piece, layout.styles())))
                                .toJavaList()))
                        .getOrElse(() -> Line.from(Iterator.concat(gutter, List.of(Span.raw(" " + line.text())))
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
                + (pieces.size() == 1
                        ? row.measured()
                        : pieces.map(piece -> width(piece.text())).sum().intValue());
        return Line.from(Iterator.concat(
                        gutter,
                        List.of(styled(marker, change.lineClass(), styles)),
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
    private static Span context(Piece piece, Theme.SpanStyles styles) {
        return piece.kind()
                .map(kind -> styled(piece.text(), syntaxClass(kind), styles))
                .getOrElse(() -> Span.raw(piece.text()));
    }

    /**
     * A piece of a changed line in {@code diffClass}: with its background, its attributes, and the colour of its
     * token, or that of {@link Theme.Classes#SYNTAX_TEXT} outside a token, which is the plain colour unless the theme
     * gives it one.
     */
    private static Span changedCode(Piece piece, String diffClass, Theme.SpanStyles styles) {
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

    private static String syntaxClass(Token.Kind kind) {
        return switch (kind) {
            case KEYWORD -> Theme.Classes.SYNTAX_KEYWORD;
            case STRING -> Theme.Classes.SYNTAX_STRING;
            case COMMENT -> Theme.Classes.SYNTAX_COMMENT;
            case NUMBER -> Theme.Classes.SYNTAX_NUMBER;
        };
    }

    private static String number(Option<Integer> number, int digits) {
        String text = number.map(String::valueOf).getOrElse("");
        return " ".repeat(digits - text.length()) + text;
    }

    private static int width(String text) {
        return Span.raw(text).width();
    }

    private static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
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
    private interface Pending {

        List<Marked> draw();
    }

    /**
     * What draws a line, and the stop of the cursor that starts on it, if one does.
     */
    private record Marked(Supplier<Line> line, Option<Mark> mark) {

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
     * @param width  how many columns its tint covers: the width of its file
     * @param hunk   the hunk of a line, which a range stays in; none for what is not a line
     */
    record Mark(Anchor anchor, int lines, int width, Option<DiffStops.HunkAt> hunk) {}

    /**
     * Where a stop of the cursor starts.
     *
     * @param mark the stop
     * @param row  the line it starts on
     */
    record Stop(Mark mark, int row) {}

    /**
     * The lines of a diff, each drawn when it shows ({@link Rows}), and where each stop of the cursor in them starts, in
     * order.
     */
    record Drawn(Rows rows, List<Stop> stops) {

        private static Drawn of(List<Marked> marked) {
            return new Drawn(
                    Rows.drawn(marked.map(Marked::line)),
                    marked.zipWithIndex().flatMap(line -> line._1().mark().map(mark -> new Stop(mark, line._2()))));
        }

        /**
         * {@code lines}, without a stop: what has no cursor.
         */
        static Drawn plain(List<Line> lines) {
            return plain(Rows.of(lines));
        }

        /**
         * {@code rows}, without a stop: what has no cursor.
         */
        static Drawn plain(Rows rows) {
            return new Drawn(rows, List.empty());
        }

        /**
         * All the lines, drawn.
         */
        List<Line> lines() {
            return rows.all();
        }

        /**
         * These lines after {@code before}, their stops moved down by as many lines.
         */
        Drawn after(List<Line> before) {
            return new Drawn(
                    rows.after(Rows.of(before)), stops.map(stop -> new Stop(stop.mark(), stop.row() + before.size())));
        }

        /**
         * These lines with what comes before their first stop as a stop of its own, the lead, if anything does.
         */
        Drawn withLead() {
            return stops.headOption()
                    .filter(first -> first.row() > 0)
                    .map(first -> new Drawn(
                            rows,
                            stops.prepend(new Stop(new Mark(new Anchor.Lead(), first.row(), 0, Option.none()), 0))))
                    .getOrElse(this);
        }
    }

    /**
     * What drawing each line of a file takes from the file: how many {@code digits} its numbers have, how many
     * columns after the gutter its changed lines are padded to, its {@code width}, how the threads on its lines are
     * drawn, whether its code is coloured by its syntax, whether the changed words of a line are highlighted, the
     * styles of the theme, and the version of the file shown in place of its diff, if one is (ADR 102).
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
            Option<FileVersion> version) {}

    /**
     * A line of a file with what drawing it takes besides the layout of its file: how many columns its text was
     * {@code measured} at, and the review threads on it.
     */
    private record Row(ParsedLine parsed, int measured, LineRemarks remarks) {}

    /**
     * How a changed line is drawn: its marker, the class of the line and that of its changed words.
     */
    private record Change(String marker, String lineClass, String wordClass) {

        static final Change ADDED = new Change("+", Theme.Classes.DIFF_ADDED_LINE, Theme.Classes.DIFF_ADDED_WORD);
        static final Change REMOVED = new Change("-", Theme.Classes.DIFF_REMOVED_LINE, Theme.Classes.DIFF_REMOVED_WORD);
    }

    /**
     * How the review threads of a file are drawn: as blocks that start {@code indent} columns in, two after the
     * gutter, and are {@code width} columns wide, as {@code style} says.
     */
    private record Blocks(int indent, int width, RemarkLines.RemarkStyle style) {

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
