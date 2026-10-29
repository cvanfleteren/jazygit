package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * A unified diff drawn rich (ADR 44): what comes before its first file as it is, then each file under a bar that
 * says what happened to it, its lines numbered in the old and the new file, and the words that changed in a changed
 * line highlighted.
 */
final class RichDiff {

    private static final String GUTTER_END = " │ ";

    private RichDiff() {}

    /**
     * The lines of {@code diff}, styled.
     */
    static List<Line> lines(String diff, Theme.SpanStyles styles) {
        UnifiedDiff parsed = UnifiedDiff.parse(diff);
        // The words of the whole diff are compared at once, as they share one budget.
        Vector<List<List<Segment>>> words = WordDiff.segments(
                        parsed.files().flatMap(FileDiff::hunks).map(Hunk::lines))
                .toVector();
        List<Tuple2<FileDiff, Vector<List<List<Segment>>>>> files = parsed.files()
                .foldLeft(
                        Tuple.of(List.<Tuple2<FileDiff, Vector<List<List<Segment>>>>>empty(), 0),
                        (done, file) -> Tuple.of(
                                done._1.prepend(Tuple.of(
                                        file,
                                        words.slice(
                                                done._2, done._2 + file.hunks().size()))),
                                done._2 + file.hunks().size()))
                ._1
                .reverse();
        return parsed.preamble()
                .map(line -> Line.from(Span.raw(line)))
                .appendAll(files.zipWithIndex()
                        .flatMap(file -> file(file._1._1, file._1._2, styles)
                                .prependAll(file._2 > 0 ? List.of(Line.from("")) : List.empty())));
    }

    /**
     * The lines of {@code file}, whose hunks have the segments {@code words}.
     */
    private static List<Line> file(FileDiff file, Vector<List<List<Segment>>> words, Theme.SpanStyles styles) {
        int digits = file.hunks()
                .flatMap(Hunk::lines)
                .flatMap(line -> line.oldNumber().toList().appendAll(line.newNumber()))
                .max()
                .map(number -> String.valueOf(number).length())
                .getOrElse(1);
        int gutter = 2 * digits + 1 + GUTTER_END.length();
        String bar = bar(file);
        int width = Math.max(
                width(bar),
                gutter
                        + file.hunks()
                                .flatMap(Hunk::lines)
                                .map(line -> width(line.text()) + 1)
                                .max()
                                .getOrElse(0));
        return List.of(Line.from(styled(padded(bar, width), Theme.Classes.DIFF_FILE_HEADER, styles)))
                .appendAll(note(file).map(text -> Line.from(styled(text, Theme.Classes.DIM, styles))))
                .appendAll(file.hunks()
                        .zip(words)
                        .zipWithIndex()
                        .flatMap(hunk -> hunk(hunk._1._1, hunk._1._2, hunk._2 == 0, digits, width - gutter, styles)));
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
     * The lines of {@code hunk}, whose lines have the segments {@code words}, under a rule that marks where it starts
     * unless it has no header: changed lines are padded to {@code width}, so their background makes a block. The rule
     * stands in for git's {@code @@ -a,b +c,d @@}, whose numbers the gutter shows already. Not the section heading git
     * gives after them: it is a line from above the hunk, which reads as one of its own. A header whose numbers cannot
     * be read, whose lines are not numbered, follows the rule as git printed it. The {@code first} hunk of a file has
     * no rule, as the bar of the file marks where it starts, unless its header has to be shown.
     */
    private static List<Line> hunk(
            Hunk hunk, List<List<Segment>> words, boolean first, int digits, int width, Theme.SpanStyles styles) {
        Span rule = styled("┄".repeat(2 * digits + 1) + GUTTER_END, Theme.Classes.DIFF_LINE_NUMBER, styles);
        List<Line> header = hunk.header().isEmpty() || (first && hunk.numbered())
                ? List.empty()
                : List.of(
                        hunk.numbered()
                                ? Line.from(rule)
                                : Line.from(rule, styled(hunk.header(), Theme.Classes.DIFF_HUNK, styles)));
        return header.appendAll(hunk.lines().zip(words).map(line -> line(line._1, line._2, digits, width, styles)));
    }

    private static Line line(DiffLine line, List<Segment> segments, int digits, int width, Theme.SpanStyles styles) {
        Span gutter = styled(
                number(line.oldNumber(), digits) + " " + number(line.newNumber(), digits) + GUTTER_END,
                Theme.Classes.DIFF_LINE_NUMBER,
                styles);
        return switch (line.kind()) {
            case CONTEXT -> Line.from(gutter, Span.raw(" " + line.text()));
            case NOTE -> Line.from(gutter, styled(line.text(), Theme.Classes.DIM, styles));
            case ADDED ->
                changed(
                        gutter,
                        "+",
                        segments,
                        width,
                        Theme.Classes.DIFF_ADDED_LINE,
                        Theme.Classes.DIFF_ADDED_WORD,
                        styles);
            case REMOVED ->
                changed(
                        gutter,
                        "-",
                        segments,
                        width,
                        Theme.Classes.DIFF_REMOVED_LINE,
                        Theme.Classes.DIFF_REMOVED_WORD,
                        styles);
        };
    }

    /**
     * A changed line: its marker and its unchanged parts in {@code lineClass}, its changed words in
     * {@code wordClass}, padded to {@code width}.
     */
    private static Line changed(
            Span gutter,
            String marker,
            List<Segment> segments,
            int width,
            String lineClass,
            String wordClass,
            Theme.SpanStyles styles) {
        int used = 1 + segments.map(segment -> width(segment.text())).sum().intValue();
        List<Span> spans = segments.map(
                        segment -> styled(segment.text(), segment.changed() ? wordClass : lineClass, styles))
                .prepend(styled(marker, lineClass, styles))
                .append(styled(" ".repeat(Math.max(0, width - used)), lineClass, styles))
                .prepend(gutter);
        return Line.from(spans.toJavaList());
    }

    private static String number(Option<Integer> number, int digits) {
        String text = number.map(String::valueOf).getOrElse("");
        return " ".repeat(digits - text.length()) + text;
    }

    private static String padded(String text, int width) {
        return text + " ".repeat(Math.max(0, width - width(text)));
    }

    private static int width(String text) {
        return Span.raw(text).width();
    }

    private static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
        return Span.styled(text, styles.of(cssClass));
    }
}
