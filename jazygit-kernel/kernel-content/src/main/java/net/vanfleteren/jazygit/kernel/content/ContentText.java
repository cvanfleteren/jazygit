package net.vanfleteren.jazygit.kernel.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.Tuple;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Stash;
import net.vanfleteren.jazygit.kernel.content.diff.RichDiff;
import net.vanfleteren.jazygit.kernel.content.diff.ShownRemarks;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.content.ShownDiff;
import net.vanfleteren.jazygit.kernel.ui.content.UnifiedLine;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import net.vanfleteren.jazygit.kernel.ui.text.Rows;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * The text of the content panel, built from what it shows: the diffs, the commits, the stashes and the pull requests,
 * styled. The log is {@link LogText}'s; the lines of an area as it shows them are {@link TextWindow}'s. Only
 * functions.
 */
public final class ContentText {

    private static final DateTimeFormatter COMMIT_DATE =
            DateTimeFormatter.ofPattern(Messages.get("content.commitDatePattern"), Locale.ENGLISH);

    private static final String MESSAGE_INDENT = "    ";

    private ContentText() {}

    /**
     * The unified diff drawn as {@code look} says, as one styled text, so it is a single element to lay out however long
     * the diff is.
     */
    public static Text diffText(ShownDiff diff, DiffLook look, Theme.SpanStyles styles) {
        return diffText(diff, look, Option.none(), styles);
    }

    /**
     * The unified diff drawn as {@code look} says, with the review threads of a pull request, {@code remarks}, if it
     * has them (ADR 55): next to their lines in the rich view, counted at the start of each file in the plain one. Why
     * they could not be read comes first.
     */
    public static Text diffText(ShownDiff diff, DiffLook look, Option<ShownRemarks> remarks, Theme.SpanStyles styles) {
        return styledText(diffDrawn(diff, look, remarks, styles).lines());
    }

    /**
     * The lines of {@link #diffText(ShownDiff, DiffLook, Option, Theme.SpanStyles)}, with the stops of the cursor in
     * the rich view (ADR 60).
     */
    public static Drawn diffDrawn(
            ShownDiff diff, DiffLook look, Option<ShownRemarks> remarks, Theme.SpanStyles styles) {
        return diffDrawn(diff, look, remarks, Option.none(), styles);
    }

    /**
     * The lines of {@link #diffText(ShownDiff, DiffLook, Option, Theme.SpanStyles)}, with the stops of the cursor in
     * the rich view (ADR 60), and the lines of {@code asGiven}, the hunk a range is open in, as git gives them (ADR
     * 83).
     */
    public static Drawn diffDrawn(
            ShownDiff diff,
            DiffLook look,
            Option<ShownRemarks> remarks,
            Option<DiffStops.HunkAt> asGiven,
            Theme.SpanStyles styles) {
        return diffDrawn(diff, look, remarks, asGiven, styles, RichDiff.RENDERED_WIDTH);
    }

    /**
     * The lines of {@link #diffDrawn(ShownDiff, DiffLook, Option, Option, Theme.SpanStyles)}, a version of its file
     * rendered {@code width} columns wide (ADR 103).
     */
    public static Drawn diffDrawn(
            ShownDiff diff,
            DiffLook look,
            Option<ShownRemarks> remarks,
            Option<DiffStops.HunkAt> asGiven,
            Theme.SpanStyles styles,
            int width) {
        List<Line> failed = remarks.flatMap(ShownRemarks::failed)
                .map(failure -> Line.from(styled(
                        Messages.get("content.remarks.failed", Placeholders.error(failure)),
                        Theme.Classes.ERROR,
                        styles)))
                .toList();
        Drawn drawn = switch (look.style()) {
            case PLAIN ->
                remarks.filter(shown -> !shown.threads().isEmpty())
                        .map(shown -> counted(diff, shown.threads(), styles))
                        .map(lines -> Drawn.plain(Rows.of(lines, Rows.Source.ofDiff(lines.map(Line::rawContent)))))
                        .getOrElse(() -> changeDrawn(diff, look, styles, width));
            case RICH, SPLIT -> RichDiff.drawn(diff.folded(), remarks, asGiven, drawing(look, styles, width));
        };
        return drawn.after(failed).withLead();
    }

    /**
     * The plain diff, each line that starts a file followed by how many of {@code threads} are on that file, if any
     * are, and that the rich view shows them.
     */
    private static List<Line> counted(ShownDiff diff, List<ReviewThread> threads, Theme.SpanStyles styles) {
        Vector<Integer> counts = diff.parsed()
                .diff()
                .files()
                .map(file -> threads.count(thread -> thread.isOn(file.path(), file.origin())))
                .toVector();
        return lines(diff.text())
                .foldLeft(Tuple.of(List.<Line>empty(), 0), (done, line) -> {
                    boolean starts = UnifiedDiff.startsFile(line);
                    int file = done._2() + (starts ? 1 : 0);
                    Line drawn = starts && file <= counts.size() && counts.get(file - 1) > 0
                            ? Line.from(
                                    styled(line, Theme.Classes.DIFF_HEADER, styles),
                                    styled(
                                            Messages.get("content.remarks.count", counts.get(file - 1)),
                                            Theme.Classes.DIFF_THREAD,
                                            styles))
                            : diffLine(line, styles);
                    return Tuple.of(done._1().prepend(drawn), file);
                })
                ._1()
                .reverse();
    }

    public static Text plainText(String content) {
        return styledText(lines(content).map(line -> Line.from(Span.raw(line))));
    }

    /**
     * A line of a unified diff, colored by what it is: added, removed, a hunk header or a file header.
     */
    public static Line diffLine(String line, Theme.SpanStyles styles) {
        return UnifiedLine.of(line, styles);
    }

    /**
     * The indented message of {@code commit}: its subject, and its body after a blank line if it has one.
     */
    static List<String> messageLines(Commit commit) {
        return List.of(MESSAGE_INDENT + commit.message())
                .appendAll(
                        commit.body().isBlank()
                                ? List.empty()
                                : lines(commit.body())
                                        .map(line -> line.isBlank() ? line : MESSAGE_INDENT + line)
                                        .prepend(""));
    }

    /**
     * The {@code Date:} line of a commit or a stash, as {@code git show} has it.
     */
    private static Span dateLine(Instant time) {
        return Span.raw(Messages.get("content.commitDate", COMMIT_DATE.format(time.atZone(ZoneId.systemDefault()))));
    }

    /**
     * Like {@code git show}: the commit's header, its indented message, a {@code ---} line and then the changes.
     */
    public static Text commitText(Commit commit, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return styledText(commitDrawn(commit, changes, look, styles).lines());
    }

    /**
     * The lines of {@link #commitText}, with the stops of the cursor in the rich view (ADR 60).
     */
    public static Drawn commitDrawn(Commit commit, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return commitDrawn(commit, changes, look, styles, RichDiff.RENDERED_WIDTH);
    }

    /**
     * The lines of {@link #commitText}, a version of its file rendered {@code width} columns wide (ADR 103).
     */
    public static Drawn commitDrawn(
            Commit commit, ShownDiff changes, DiffLook look, Theme.SpanStyles styles, int width) {
        List<String> message = messageLines(commit);
        return changeDrawn(changes, look, styles, width)
                .after(List.of(
                                styled(
                                        Messages.get(
                                                "content.commitSha",
                                                commit.sha().value()),
                                        Theme.Classes.SHA,
                                        styles),
                                Span.raw(Messages.get(
                                        "content.commitAuthor", commit.authorName(), commit.authorEmail())),
                                dateLine(commit.authorTime()),
                                Span.raw(""),
                                styled(message.head(), Theme.Classes.BOLD, styles))
                        .map(Line::from)
                        .appendAll(message.tail().map(line -> Line.from(Span.raw(line))))
                        .append(Line.from(Span.raw("---"))))
                .withLead();
    }

    /**
     * Like {@link #commitText}: the stash's name and message, the branch it was stashed on if its message says, when
     * it was stashed, a {@code ---} line and then the changes.
     */
    public static Text stashText(Stash stash, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return styledText(stashDrawn(stash, changes, look, styles).lines());
    }

    /**
     * The lines of {@link #stashText}, with the stops of the cursor in the rich view (ADR 60).
     */
    public static Drawn stashDrawn(Stash stash, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return stashDrawn(stash, changes, look, styles, RichDiff.RENDERED_WIDTH);
    }

    /**
     * The lines of {@link #stashText}, a version of its file rendered {@code width} columns wide (ADR 103).
     */
    public static Drawn stashDrawn(Stash stash, ShownDiff changes, DiffLook look, Theme.SpanStyles styles, int width) {
        return changeDrawn(changes, look, styles, width)
                .after(List.of(styled(
                                Messages.get("content.stashHeader", stash.ref(), stash.message()),
                                Theme.Classes.SHA,
                                styles))
                        .appendAll(stash.branch()
                                .map(branch -> Span.raw(Messages.get("content.stashBranch", branch.value()))))
                        .append(dateLine(stash.time()))
                        .map(Line::from)
                        .append(Line.from(Span.raw("---"))))
                .withLead();
    }

    /**
     * A unified diff, drawn as {@code look} says.
     */
    public static List<Line> changeLines(ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return changeDrawn(changes, look, styles, RichDiff.RENDERED_WIDTH).lines();
    }

    /**
     * A unified diff, drawn as {@code look} says, with the stops of the cursor in the rich view (ADR 60).
     */
    private static Drawn changeDrawn(ShownDiff changes, DiffLook look, Theme.SpanStyles styles, int width) {
        return switch (look.style()) {
            case PLAIN -> {
                List<String> lines = lines(changes.text());
                // Searched as git printed it, each line of a hunk without its prefix (ADR 126).
                yield Drawn.plain(Rows.drawn(
                        lines.map(line -> () -> diffLine(line, styles)),
                        Rows.Source.ofDiff(lines).map(source -> () -> source)));
            }
            case RICH, SPLIT ->
                RichDiff.drawn(changes.folded(), Option.none(), Option.none(), drawing(look, styles, width));
        };
    }

    /**
     * How the rich view draws a diff as {@code look} says, in {@code styles}, in an area {@code width} columns wide.
     */
    private static RichDiff.Drawing drawing(DiffLook look, Theme.SpanStyles styles, int width) {
        return RichDiff.Drawing.of(styles)
                .withHighlights(HashSet.of(RichDiff.Highlight.values()).filter(highlight -> switch (highlight) {
                    case SYNTAX -> look.syntax();
                    case WORDS -> look.words();
                }))
                .withWidth(width)
                .withSplit(look.style() == DiffStyle.SPLIT ? RichDiff.Split.SIDE_BY_SIDE : RichDiff.Split.UNIFIED);
    }

    static List<String> lines(String text) {
        return List.ofAll(text.lines());
    }

    static Text styledText(List<Line> lines) {
        return Text.from(lines.toJavaList());
    }

    static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
        return Span.styled(text, styles.of(cssClass));
    }
}
