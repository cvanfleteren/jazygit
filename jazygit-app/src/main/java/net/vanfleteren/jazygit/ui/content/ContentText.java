package net.vanfleteren.jazygit.ui.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractList;
import java.util.Locale;
import java.util.function.IntPredicate;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffLines;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.ui.AheadView;
import net.vanfleteren.jazygit.ui.ContentView.Body;
import net.vanfleteren.jazygit.ui.ContentView.ShownDiff;
import net.vanfleteren.jazygit.ui.ContentView.ShownRemarks;
import net.vanfleteren.jazygit.ui.LogGraph;
import net.vanfleteren.jazygit.ui.Placeholders;
import net.vanfleteren.jazygit.ui.RichDiff;
import net.vanfleteren.jazygit.ui.Rows;
import net.vanfleteren.jazygit.ui.TextWidth;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The text of the content panel, built from what it shows: the diffs, the log, the commits, the stashes and the pull
 * requests, styled, and the lines of an area as it shows them. Only functions.
 */
public final class ContentText {

    private static final DateTimeFormatter COMMIT_DATE =
            DateTimeFormatter.ofPattern(Messages.get("content.commitDatePattern"), Locale.ENGLISH);
    private static final DateTimeFormatter LOG_DATE_TIME =
            DateTimeFormatter.ofPattern(Messages.get("content.dateTimePattern"));

    private static final String MESSAGE_INDENT = "    ";
    /**
     * How the header lines of a diff start, before its first hunk.
     */
    private static final List<String> DIFF_HEADER_PREFIXES = List.of("+++", "---", "diff ", "index ");
    /**
     * What an area is given in place of a line that does not show ({@link #windowed}).
     */
    private static final Line BLANK = Line.from(Span.raw(""));

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
    public static RichDiff.Drawn diffDrawn(
            ShownDiff diff, DiffLook look, Option<ShownRemarks> remarks, Theme.SpanStyles styles) {
        return diffDrawn(diff, look, remarks, Option.none(), styles);
    }

    /**
     * The lines of {@link #diffText(ShownDiff, DiffLook, Option, Theme.SpanStyles)}, with the stops of the cursor in
     * the rich view (ADR 60), and the lines of {@code asGiven}, the hunk a range is open in, as git gives them (ADR
     * 83).
     */
    public static RichDiff.Drawn diffDrawn(
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
    public static RichDiff.Drawn diffDrawn(
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
        RichDiff.Drawn drawn = switch (look.style()) {
            case PLAIN ->
                RichDiff.Drawn.plain(remarks.filter(shown -> !shown.threads().isEmpty())
                        .map(shown -> counted(diff, shown.threads(), styles))
                        .getOrElse(() -> changeLines(diff, look, styles)));
            case RICH -> RichDiff.drawn(diff.folded(), look.syntax(), look.words(), remarks, asGiven, styles, width);
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

    /**
     * The branch log, each commit as {@link #logLines} lists it: the {@code commit <sha>} part of each commit's first line is
     * yellow. The commits on the base are dimmed instead, and the fork point is marked after its sha.
     */
    public static Text logText(List<Commit> commits, ZoneId zone, AheadView ahead, Theme.SpanStyles styles) {
        DateTimeFormatter dateTime = dateTime(zone);
        return styledText(drawn(commits).flatMap(node -> {
            Sha sha = node._1.commit().sha();
            List<String> lines = logLines(node._1, node._2, dateTime, ahead.mark(sha));
            return ahead.dimmed(sha)
                    ? lines.map(line -> Line.from(styled(line, Theme.Classes.DIM, styles)))
                    : lines.map(line -> line.startsWith("* ")
                            ? Line.from(Span.raw("* "), styled(line.substring(2), Theme.Classes.CURRENT_BRANCH, styles))
                            : Line.from(Span.raw(line)));
        }));
    }

    public static Text plainText(String content) {
        return styledText(lines(content).map(line -> Line.from(Span.raw(line))));
    }

    /**
     * {@code line} scrolled {@code column} columns sideways: without its first columns, its spans kept in their
     * styles, as {@link TextWidth#rest} cuts their text.
     */
    public static Line sideways(Line line, int column) {
        return Line.from(List.ofAll(line.spans())
                .foldLeft(
                        Tuple.of(column, List.<Span>empty()),
                        (done, span) -> done._1() == 0
                                ? Tuple.of(0, done._2().prepend(span))
                                : Tuple.of(
                                        Math.max(0, done._1() - span.width()),
                                        done._1() >= span.width()
                                                ? done._2()
                                                : done._2()
                                                        .prepend(new Span(
                                                                TextWidth.rest(span.content(), done._1()),
                                                                span.style()))))
                ._2()
                .reverse()
                .toJavaList());
    }

    /**
     * {@code lines} as an area shows them from {@code row}, in an area {@code height} lines high: the lines that show,
     * scrolled {@code column} columns sideways, and a blank one in place of each of the others. TamboUI's text area
     * measures how wide every line it is given is, each frame, so a long diff would hold up every frame; it still gets
     * as many lines, for its scrollbar and how far it scrolls. The height is that of the frame before, so the lines are
     * kept that would show in an area up to twice as high, which the element scrolls up from its last line at its
     * bottom; none before the area was ever drawn, as that frame is only drawn to learn its size and then drawn again
     * ({@code Fitted}), and drawing every line of a long diff for it took as long as styling it. Those of them that are
     * {@code filling} are
     * {@link #filled} to the edge of an area {@code width} columns wide (ADR 80). Only the lines that show are drawn
     * ({@link Rows}).
     */
    public static Text windowed(Rows lines, int column, int row, int height, int width, IntPredicate filling) {
        Tuple2<Integer, Integer> window = window(lines.size(), row, height);
        int top = window._1();
        int bottom = window._2();
        // Text copies the lines it is given, so each line that shows is cut once.
        return Text.from(new AbstractList<Line>() {
            @Override
            public Line get(int index) {
                if (index < top || index >= bottom) {
                    return BLANK;
                }
                Line drawn = lines.get(index);
                Line line = filling.test(index) ? filled(drawn, column + width) : drawn;
                return column == 0 ? line : sideways(line, column);
            }

            @Override
            public int size() {
                return lines.size();
            }
        });
    }

    /**
     * The lines of {@code size} that {@link #windowed} shows from {@code row} in an area {@code height} lines high: from
     * the first up to, not with, the second; none at {@code row} while the area was never drawn, its height 0.
     */
    public static Tuple2<Integer, Integer> window(int size, int row, int height) {
        int high = Math.max(0, 2 * height);
        int top = Math.clamp(row, 0, Math.max(0, size - high));
        return Tuple.of(top, Math.max(top, Math.min(size, row + high)));
    }

    /**
     * {@code line} as wide as {@code width}, if its last span is blank: the padding of a changed line, of the bar of a
     * file or of the tint of the cursor, lengthened so its background goes on to the edge of the area (ADR 80).
     */
    public static Line filled(Line line, int width) {
        List<Span> spans = List.ofAll(line.spans());
        int missing = width - line.width();
        return spans.lastOption()
                .filter(last -> missing > 0 && last.content().isBlank())
                .map(last -> Line.from(spans.init()
                        .append(new Span(last.content() + " ".repeat(missing), last.style()))
                        .toJavaList()))
                .getOrElse(line);
    }

    /**
     * A line of a unified diff, colored by what it is: added, removed, a hunk header or a file header.
     */
    public static Line diffLine(String line, Theme.SpanStyles styles) {
        return diffClass(line)
                .map(cssClass -> Line.from(styled(line, cssClass, styles)))
                .getOrElse(() -> Line.from(Span.raw(line)));
    }

    private static Option<String> diffClass(String line) {
        return switch (line) {
            case String l when DIFF_HEADER_PREFIXES.exists(l::startsWith) -> Option.of(Theme.Classes.DIFF_HEADER);
            case String l when l.startsWith("+") -> Option.of(Theme.Classes.DIFF_ADDED);
            case String l when l.startsWith("-") -> Option.of(Theme.Classes.DIFF_REMOVED);
            case String l when l.startsWith("@@") -> Option.of(Theme.Classes.DIFF_HUNK);
            default -> Option.none();
        };
    }

    /**
     * The commits as a {@code git log}-style listing, with dates in {@code zone}. Like a graph, the first
     * line of each commit starts with {@code "* "} and its other lines with {@code "| "}. The message is
     * indented.
     */
    public static List<String> logLines(List<Commit> commits, ZoneId zone) {
        DateTimeFormatter dateTime = dateTime(zone);
        return drawn(commits).flatMap(node -> logLines(node._1, node._2, dateTime, Option.none()));
    }

    /**
     * The lines of the commit of {@code node} in {@link #logLines(List, ZoneId)}, drawn with {@code gutter}, with
     * {@code mark} after its sha.
     */
    private static List<String> logLines(
            CommitGraph.Node node, LogGraph.Gutter gutter, DateTimeFormatter dateTime, Option<String> mark) {
        Commit c = node.commit();
        List<String> lines = List.of(
                        Messages.get("content.commitSha", c.shortSha())
                                + mark.map(text -> "  " + text).getOrElse(""),
                        Messages.get("content.author", c.authorName(), c.authorEmail()),
                        dateTime.format(c.authorTime()),
                        "")
                .appendAll(messageLines(c))
                .append("");
        return gutter.draw(
                lines,
                Messages.get("content.merge", c.parents().map(Sha::shortSha).mkString(" ")));
    }

    /**
     * The nodes of the graph of {@code commits}, each with the gutter its lines are drawn with.
     */
    private static List<Tuple2<CommitGraph.Node, LogGraph.Gutter>> drawn(List<Commit> commits) {
        List<CommitGraph.Node> nodes = CommitGraph.layout(commits);
        return nodes.zip(LogGraph.gutters(nodes));
    }

    /**
     * How the log shows when a commit was made, in {@code zone}.
     */
    private static DateTimeFormatter dateTime(ZoneId zone) {
        return LOG_DATE_TIME.withZone(zone);
    }

    /**
     * The indented message of {@code commit}: its subject, and its body after a blank line if it has one.
     */
    private static List<String> messageLines(Commit commit) {
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
    public static RichDiff.Drawn commitDrawn(Commit commit, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return commitDrawn(commit, changes, look, styles, RichDiff.RENDERED_WIDTH);
    }

    /**
     * The lines of {@link #commitText}, a version of its file rendered {@code width} columns wide (ADR 103).
     */
    public static RichDiff.Drawn commitDrawn(
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
    public static RichDiff.Drawn stashDrawn(Stash stash, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return stashDrawn(stash, changes, look, styles, RichDiff.RENDERED_WIDTH);
    }

    /**
     * The lines of {@link #stashText}, a version of its file rendered {@code width} columns wide (ADR 103).
     */
    public static RichDiff.Drawn stashDrawn(
            Stash stash, ShownDiff changes, DiffLook look, Theme.SpanStyles styles, int width) {
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
    private static RichDiff.Drawn changeDrawn(ShownDiff changes, DiffLook look, Theme.SpanStyles styles, int width) {
        return switch (look.style()) {
            case PLAIN ->
                RichDiff.Drawn.plain(Rows.drawn(lines(changes.text()).map(line -> () -> diffLine(line, styles))));
            case RICH ->
                RichDiff.drawn(
                        changes.folded(), look.syntax(), look.words(), Option.none(), Option.none(), styles, width);
        };
    }

    private static List<String> lines(String text) {
        return List.ofAll(text.lines());
    }

    private static Text styledText(List<Line> lines) {
        return Text.from(lines.toJavaList());
    }

    private static Span styled(String text, String cssClass, Theme.SpanStyles styles) {
        return Span.styled(text, styles.of(cssClass));
    }

    /**
     * Whether {@code body} shows a version of a file rendered (ADR 103), which is drawn as wide as its area.
     */
    public static boolean rendering(Body body) {
        Option<ShownDiff> diff = switch (body) {
            case Body.Diff(ShownDiff shown, var look, var remarks) -> Option.of(shown);
            case Body.CommitChanges(var commit, ShownDiff changes, var look) -> Option.of(changes);
            case Body.StashChanges(var stash, ShownDiff changes, var look) -> Option.of(changes);
            case Body.BranchLog _, Body.PullRequest _ -> Option.none();
        };
        return diff.flatMap(ShownDiff::version)
                .exists(version -> version.rendering().isDefined());
    }

    /**
     * The hunk of the line {@code mark} in {@code body}, found in its diff without drawing it: the hunk a range from
     * that line is open in (ADR 83). Numbered as the rich view numbers them, among the hunks of the diff as its lines
     * left out are shown (ADR 77). None unless {@code body} is a diff with that line: only the diff of a pull request's
     * files has ranges.
     */
    public static Option<DiffStops.HunkAt> markedHunk(Body body, Anchor mark) {
        return body instanceof Body.Diff(ShownDiff diff, var look, var remarks)
                ? DiffLines.of(diff.folded().diff().diff())
                        .lines()
                        .find(line -> line.anchor().equals(mark))
                        .map(DiffLines.Line::hunk)
                : Option.none();
    }

    /**
     * Like a commit: a header with the pull request, who opened it, the branches it merges and when it changed,
     * then its indented title and description, a {@code ---} line, and what it changes.
     */
    public static Text pullRequestText(PullRequestSummary pr, PullRequestPreview preview, Theme.SpanStyles styles) {
        List<String> body = lines(preview.body());
        return styledText(List.of(
                        styled(
                                Messages.get(
                                        "content.pullRequest.header", pr.repository(), String.valueOf(pr.number())),
                                Theme.Classes.SHA,
                                styles),
                        Span.raw(Messages.get("content.pullRequest.author", pr.author())),
                        Span.raw(Messages.get(
                                pr.fromFork() ? "content.pullRequest.forkBranches" : "content.pullRequest.branches",
                                pr.headBranch().name(),
                                preview.base().name())),
                        Span.raw(Messages.get(
                                "content.pullRequest.updated",
                                COMMIT_DATE.format(pr.updatedAt().atZone(ZoneId.systemDefault())))),
                        Span.raw(""),
                        styled(MESSAGE_INDENT + pr.title(), Theme.Classes.BOLD, styles))
                .map(Line::from)
                .appendAll((body.isEmpty()
                                ? List.<String>empty()
                                : body.map(line -> line.isBlank() ? "" : MESSAGE_INDENT + line)
                                        .prepend(""))
                        .map(line -> Line.from(Span.raw(line))))
                .append(Line.from(Span.raw("---")))
                .appendAll(lines(
                                preview.truncated()
                                        ? preview.diff() + Messages.get("git.diff.truncated") + "\n"
                                        : preview.diff())
                        .map(line -> diffLine(line, styles))));
    }
}
