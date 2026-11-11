package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.RichTextState;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractList;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.DiffLines;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.ContentView.Body;
import net.vanfleteren.jazygit.ui.ContentView.Shown;
import net.vanfleteren.jazygit.ui.ContentView.ShownDiff;
import net.vanfleteren.jazygit.ui.ContentView.ShownRemarks;
import net.vanfleteren.jazygit.ui.ContentView.Tone;

/**
 * The right-hand content panel: it draws what {@link ContentView} says it shows, styling the diffs, the log, the
 * commits, the stashes and the pull requests, and keeps the areas that scroll through them.
 */
public final class ContentPanel {

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

    private final Theme.SpanStyles styles;
    // The scrollable areas for the diffs, the branch log and the commit. Where they are scrolled to lives in the
    // model; the elements are kept between renders as they also lay out the text for the scrollbar. Only used from
    // the render thread.
    private final Map<ContentArea, ScrollArea> areas;

    /**
     * @param styles          how the diffs and the log are colored
     * @param leaveScrollArea called when Escape, or Left at the left edge of an area, is pressed; gives the focus
     *                        back to the pane if an area had it, and says whether it did
     */
    public ContentPanel(Theme.SpanStyles styles, BooleanSupplier leaveScrollArea) {
        this(styles, leaveScrollArea, id -> {});
    }

    /**
     * @param focus called with the id of a focusable area that is clicked, to give it the focus
     */
    public ContentPanel(Theme.SpanStyles styles, BooleanSupplier leaveScrollArea, Consumer<String> focus) {
        this(styles, leaveScrollArea, focus, msg -> {});
    }

    /**
     * @param dispatch receives where the user scrolled an area to, on the render thread
     */
    public ContentPanel(
            Theme.SpanStyles styles, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
        this.styles = styles;
        areas = List.of(ContentArea.values())
                .toLinkedMap(area -> area, area -> new ScrollArea(area, leaveScrollArea, focus, dispatch));
    }

    /**
     * Whether {@code id} is an area of the content panel that can have the focus, rather than a pane.
     */
    public static boolean isArea(String id) {
        return ContentArea.focusable(id).isDefined();
    }

    /**
     * The unified diff drawn as {@code look} says, as one styled text, so it is a single element to lay out however long
     * the diff is.
     */
    static Text diffText(ShownDiff diff, DiffLook look, Theme.SpanStyles styles) {
        return diffText(diff, look, Option.none(), styles);
    }

    /**
     * The unified diff drawn as {@code look} says, with the review threads of a pull request, {@code remarks}, if it
     * has them (ADR 55): next to their lines in the rich view, counted at the start of each file in the plain one. Why
     * they could not be read comes first.
     */
    static Text diffText(ShownDiff diff, DiffLook look, Option<ShownRemarks> remarks, Theme.SpanStyles styles) {
        return styledText(diffDrawn(diff, look, remarks, styles).lines());
    }

    /**
     * The lines of {@link #diffText(ShownDiff, DiffLook, Option, Theme.SpanStyles)}, with the stops of the cursor in
     * the rich view (ADR 60).
     */
    static RichDiff.Drawn diffDrawn(
            ShownDiff diff, DiffLook look, Option<ShownRemarks> remarks, Theme.SpanStyles styles) {
        return diffDrawn(diff, look, remarks, Option.none(), styles);
    }

    /**
     * The lines of {@link #diffText(ShownDiff, DiffLook, Option, Theme.SpanStyles)}, with the stops of the cursor in
     * the rich view (ADR 60), and the lines of {@code asGiven}, the hunk a range is open in, as git gives them (ADR
     * 83).
     */
    static RichDiff.Drawn diffDrawn(
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
    static RichDiff.Drawn diffDrawn(
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
    static Text logText(List<Commit> commits, ZoneId zone, AheadView ahead, Theme.SpanStyles styles) {
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

    static Text plainText(String content) {
        return styledText(lines(content).map(line -> Line.from(Span.raw(line))));
    }

    /**
     * {@code line} scrolled {@code column} columns sideways: without its first columns, its spans kept in their
     * styles, as {@link TextWidth#rest} cuts their text.
     */
    static Line sideways(Line line, int column) {
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
    static Text windowed(Rows lines, int column, int row, int height, int width, Set<Integer> filling) {
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
                Line line = filling.contains(index) ? filled(drawn, column + width) : drawn;
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
    static Tuple2<Integer, Integer> window(int size, int row, int height) {
        int high = Math.max(0, 2 * height);
        int top = Math.clamp(row, 0, Math.max(0, size - high));
        return Tuple.of(top, Math.max(top, Math.min(size, row + high)));
    }

    /**
     * {@code line} as wide as {@code width}, if its last span is blank: the padding of a changed line, of the bar of a
     * file or of the tint of the cursor, lengthened so its background goes on to the edge of the area (ADR 80).
     */
    static Line filled(Line line, int width) {
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
    static Line diffLine(String line, Theme.SpanStyles styles) {
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
    static List<String> logLines(List<Commit> commits, ZoneId zone) {
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
    static Text commitText(Commit commit, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return styledText(commitDrawn(commit, changes, look, styles).lines());
    }

    /**
     * The lines of {@link #commitText}, with the stops of the cursor in the rich view (ADR 60).
     */
    static RichDiff.Drawn commitDrawn(Commit commit, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return commitDrawn(commit, changes, look, styles, RichDiff.RENDERED_WIDTH);
    }

    /**
     * The lines of {@link #commitText}, a version of its file rendered {@code width} columns wide (ADR 103).
     */
    static RichDiff.Drawn commitDrawn(
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
    static Text stashText(Stash stash, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return styledText(stashDrawn(stash, changes, look, styles).lines());
    }

    /**
     * The lines of {@link #stashText}, with the stops of the cursor in the rich view (ADR 60).
     */
    static RichDiff.Drawn stashDrawn(Stash stash, ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return stashDrawn(stash, changes, look, styles, RichDiff.RENDERED_WIDTH);
    }

    /**
     * The lines of {@link #stashText}, a version of its file rendered {@code width} columns wide (ADR 103).
     */
    static RichDiff.Drawn stashDrawn(
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
    static List<Line> changeLines(ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
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
     * The pane that gets the focus back when Escape, or Left at its left edge, is pressed in the area with id
     * {@code areaId}, if that is an area: the pane whose highlighted row it shows.
     */
    public Option<PaneId> paneOfArea(Model model, String areaId) {
        return ContentArea.focusable(areaId).map(area -> area.pane(model));
    }

    /**
     * The id of the area that shows what the pane {@code paneId} has highlighted, to move the focus into:
     * the staged diff if it shows anything, else the unstaged one, the commit or the log; none while it shows nothing.
     */
    public Option<String> areaOfPane(Model model, String paneId) {
        return ContentView.of(model, paneId)
                .areas()
                .iterator()
                .map(Shown.Area::area)
                .find(area -> area.pane(model).value().equals(paneId))
                .map(area -> area.id().value());
    }

    /**
     * What goes with the pane that has the focus, {@code focusedId}; while a popup has it, what goes with the
     * pane the popup was opened from. That holds also in the frame a popup closes in, as it still has the focus
     * until the focus is given back.
     */
    public StyledElement<?> render(Model model, String focusedId) {
        tabbableWhileIn(focusedId, model);
        return column(element(ContentView.of(model, focusedId), model, focusedId));
    }

    /**
     * How {@code shown} is drawn: a message in a panel, or the areas, where the model has them scrolled to.
     */
    private Element element(Shown shown, Model model, String focusedId) {
        return switch (shown) {
            case Shown.Message(String title, String content, Tone tone) ->
                panel(
                                title,
                                switch (tone) {
                                    case DIM -> text(content).addClass(Theme.Classes.DIM);
                                    case ERROR -> text(content).addClass(Theme.Classes.ERROR);
                                })
                        .fill();
            case Shown.Area area -> area(area.area()).show(model, focusedId, area, this::bodyDrawn, styles);
            case Shown.Stacked(List<Shown.Area> stacked) ->
                column(stacked.<Element>map(area -> element(area, model, focusedId))
                                .toJavaArray(Element[]::new))
                        .fill();
        };
    }

    /**
     * {@code body}, styled, with the stops of the cursor of a rich diff (ADR 60), the lines of {@code asGiven}, the
     * hunk a range is open in, as git gives them (ADR 83), and a version of a file rendered {@code width} columns wide
     * (ADR 103). Only the diff of a pull request's files has ranges.
     */
    private RichDiff.Drawn bodyDrawn(Body body, Option<DiffStops.HunkAt> asGiven, int width) {
        return switch (body) {
            case Body.Diff(ShownDiff diff, DiffLook look, var remarks) ->
                diffDrawn(diff, look, remarks, asGiven, styles, width);
            case Body.BranchLog(List<Commit> commits, AheadView ahead) ->
                plain(logText(commits, ZoneId.systemDefault(), ahead, styles));
            case Body.CommitChanges(Commit commit, ShownDiff changes, DiffLook look) ->
                commitDrawn(commit, changes, look, styles, width);
            case Body.StashChanges(Stash stash, ShownDiff changes, DiffLook look) ->
                stashDrawn(stash, changes, look, styles, width);
            case Body.PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) ->
                plain(pullRequestText(pullRequest, preview, styles));
        };
    }

    /**
     * The hunk of the line {@code mark} in {@code body}, found in its diff without drawing it: the hunk a range from
     * that line is open in (ADR 83). Numbered as the rich view numbers them, among the hunks of the diff as its lines
     * left out are shown (ADR 77). None unless {@code body} is a diff with that line: only the diff of a pull request's
     * files has ranges.
     */
    /**
     * Whether {@code body} shows a version of a file rendered (ADR 103), which is drawn as wide as its area.
     */
    static boolean rendering(Body body) {
        Option<ShownDiff> diff = switch (body) {
            case Body.Diff(ShownDiff shown, var look, var remarks) -> Option.of(shown);
            case Body.CommitChanges(var commit, ShownDiff changes, var look) -> Option.of(changes);
            case Body.StashChanges(var stash, ShownDiff changes, var look) -> Option.of(changes);
            case Body.BranchLog _, Body.PullRequest _ -> Option.none();
        };
        return diff.flatMap(ShownDiff::version)
                .exists(version -> version.rendering().isDefined());
    }

    static Option<DiffStops.HunkAt> markedHunk(Body body, Anchor mark) {
        return body instanceof Body.Diff(ShownDiff diff, var look, var remarks)
                ? DiffLines.of(diff.folded().diff().diff())
                        .lines()
                        .find(line -> line.anchor().equals(mark))
                        .map(DiffLines.Line::hunk)
                : Option.none();
    }

    private static RichDiff.Drawn plain(Text text) {
        return RichDiff.Drawn.plain(List.ofAll(text.lines()));
    }

    /**
     * Lets Tab move between the areas only while one of them, {@code focusedId}, has the focus, or had it
     * before a popup took it: the area then takes the focus back when the popup closes, as from the popup of a review
     * comment (ADR 60).
     */
    private void tabbableWhileIn(String focusedId, Model model) {
        boolean inContent = isArea(focusedId) || model.shownPane().exists(ContentPanel::isArea);
        areas.values().forEach(area -> area.tabbable(inContent));
    }

    private ScrollArea area(ContentArea area) {
        return areas.apply(area);
    }

    /**
     * Like a commit: a header with the pull request, who opened it, the branches it merges and when it changed,
     * then its indented title and description, a {@code ---} line, and what it changes.
     */
    static Text pullRequestText(PullRequestSummary pr, PullRequestPreview preview, Theme.SpanStyles styles) {
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

    /**
     * A scrollable area of the panel. It shows what it is given at the position the model has for that, and tells
     * where the user scrolled it to; the element only keeps the layout of the text.
     */
    private static final class ScrollArea {

        private static final int WHEEL_LINES = 3;
        // The keys that move the cursor of a rich diff by a stop besides the arrows, as the lists of the panes are
        // moved (ADR 60).
        private static final char UP = 'k';
        private static final char DOWN = 'j';
        // The keys that scroll an area sideways besides the arrows, to the end of a long line or a wide code block.
        private static final char LEFT = 'h';
        private static final char RIGHT = 'l';
        private static final Keymap KEYMAP = Keymap.standard();

        private final ContentArea area;
        private final String id;
        private final RichTextAreaElement element;
        private final BooleanSupplier leaveScrollArea;
        private final Consumer<String> focus;
        private final Consumer<Msg> dispatch;
        // The body last shown, the theme it was styled in and its text: a frame that shows the same, like one drawn
        // only to turn a spinner, or a move of the cursor, does not style it again.
        private Option<Styled> styled = Option.none();
        // The cursor of the rich diff shown, as last drawn: what the keys and the wheel move (ADR 60).
        private Option<DiffCursor> cursor = Option.none();
        // Where the model had the area when it was last shown, and where the area told it it went from there: until
        // the model has it, a frame drawn meanwhile shows the latter, so a key pressed right after another acts on
        // where that one took the cursor (ADR 60).
        private Scrolled shownAt = Scrolled.top(new ScrolledContent.Diff(DiffText.of("")));
        private Option<Tuple2<Scrolled, Scrolled>> sent = Option.none();
        // Whether it shows the diff of a pull request's files, whose keys comment on it (ADR 60).
        private boolean review = false;
        // How far the area is scrolled sideways, as last drawn, and how far it can be. TamboUI's text area keeps a
        // column it never draws at, so the area draws its lines with as many columns cut off their start itself.
        private int column = 0;
        private int columns = 0;
        // The size the area was last drawn in, which its lines are fitted to; none before it is first drawn (ADR 80).
        private int drawnWidth = -1;
        private int drawnHeight = -1;

        ScrollArea(ContentArea area, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
            this.area = area;
            this.id = area.id().value();
            this.leaveScrollArea = leaveScrollArea;
            this.focus = focus;
            this.dispatch = dispatch;
            element = richTextArea().id(id).scrollbar().fill();
        }

        /**
         * Makes the area part of the Tab cycle while {@code inContent}, so Tab goes from one area to the
         * other then, but from the last pane back to the first otherwise.
         */
        void tabbable(boolean inContent) {
            element.focusable(inContent);
        }

        /**
         * The area as {@code shown}, its body as {@code style} styles it, where the model has it scrolled to, its lines
         * fitted to the size it is drawn in (ADR 80).
         */
        Element show(Model model, String focusedId, Shown.Area shown, Styler style, Theme.SpanStyles styles) {
            return new Fitted(
                    fit(model, focusedId, shown, style, styles), () -> fit(model, focusedId, shown, style, styles));
        }

        /**
         * The text area as {@code shown}, as {@link #show} says, its lines fitted to the size the area was last drawn
         * in, which the text area only learns while it draws.
         */
        private RichTextAreaElement fit(
                Model model, String focusedId, Shown.Area shown, Styler style, Theme.SpanStyles styles) {
            ThemeName theme = model.preferences().theme();
            ScrolledContent content = shown.content();
            Scrolled known = model.scrolled(area.id(), content);
            sent = sent.filter(told -> told._1().equals(known));
            Scrolled told = sent.map(Tuple2::_2).getOrElse(known);
            // What was drawn before, for the same changes drawn anew, as when a version of the file shows in place of
            // its diff (ADR 102): the line of the cursor stays where it was on the screen then too.
            Option<Styled> sameChanges = styled.filter(
                    before -> before.theme() == theme && shownAt.content().sameChanges(content));
            shownAt = known;
            Body body = shown.body();
            // A version rendered is drawn as wide as the area, which it learns while it draws (ADR 80, 103).
            int width = element.state().viewportWidth() > 0 ? element.state().viewportWidth() : RichDiff.RENDERED_WIDTH;
            Option<Styled> last = styled.filter(before -> before.body().equals(body)
                    && before.theme() == theme
                    && (!rendering(body) || before.width() == width));
            // The hunk a range is open in is shown as git gives it, so a range holds the lines it shows (ADR 83). A new
            // body has its hunk looked up in its diff, so it is drawn once, in the order it is shown in.
            Option<DiffStops.HunkAt> asGiven = told.mark()
                    .flatMap(mark -> last.map(before -> before.hunk(mark)).getOrElse(() -> markedHunk(body, mark)));
            Styled current = last.filter(before -> before.asGiven().equals(asGiven))
                    .getOrElse(() -> Styled.of(body, theme, asGiven, style.drawn(body, asGiven, width), width));
            styled = Option.of(current);
            // Shown in another order, or as another version of the file, the line of the cursor stays where it was on
            // the screen: the view moves by as much as it does, which the model learns with the next move (ADR 83,
            // 102). A line the cursor was moved to that was not drawn before takes the place of the line the cursor
            // was drawn on.
            Option<Anchor> drawnOn = cursor.map(DiffCursor::anchor);
            Scrolled at = last.orElse(sameChanges)
                    .filter(before -> before != current)
                    .flatMap(before -> told.cursor()
                            .flatMap(cursorAt -> before.row(cursorAt)
                                    .orElse(() -> drawnOn.flatMap(before::row))
                                    .flatMap(was -> current.row(cursorAt).map(is -> is - was))))
                    .filter(shift -> shift != 0)
                    .map(shift -> {
                        Scrolled shifted = new Scrolled(
                                told.content(),
                                Math.max(0, told.row() + shift),
                                told.column(),
                                told.cursor(),
                                told.mark());
                        sent = Option.of(Tuple.of(known, shifted));
                        return shifted;
                    })
                    .getOrElse(told);
            RichTextState state = element.state();
            cursor = DiffCursor.of(
                    current.stops(),
                    current.indexes(),
                    current.lines().size(),
                    state.viewportHeight(),
                    at.row(),
                    at.cursor(),
                    at.mark());
            review = shown.body() instanceof Body.Diff(var diff, var look, var remarks) && remarks.isDefined();
            boolean focused = id.equals(focusedId);
            // The size the element was last drawn in, which a frame in another size draws it in first (ADR 80).
            columns = Math.max(0, current.widest() - state.viewportWidth());
            column = Math.clamp(at.column(), 0, columns);
            int row = cursor.map(DiffCursor::row).getOrElse(at.row());
            // The lines that show are kept drawn for the frames after, and only they ({@link Rows}).
            Tuple2<Integer, Integer> window = window(current.lines().size(), row, state.viewportHeight());
            Rows kept = current.lines().keeping(window._1(), window._2());
            styled = Option.of(current.withLines(kept));
            Rows lines = cursor.filter(ignored -> focused)
                    .map(on -> on.tinted(kept, styles.of(Theme.Classes.DIFF_CURSOR)))
                    .getOrElse(kept);
            element.text(windowed(lines, column, row, state.viewportHeight(), state.viewportWidth(), current.filling()))
                    .title(shown.title());
            // The state clamps a scroll to the lines it was last drawn with: a longer body, as a version of the file
            // in place of its diff, would show only the blank lines around where it was cut off (ADR 78, 102).
            state.setContentHeight(current.lines().size());
            state.scrollToTop();
            state.scrollDown(row);
            return element.onKeyEvent(event -> keys(event, focused, content))
                    .onMouseEvent(event -> mouse(event, content));
        }

        /**
         * What {@code event} does: Left leaves the area as Escape does once it is scrolled all the way left, as Right in
         * its pane moves into it.
         */
        private EventResult keys(KeyEvent event, boolean focused, ScrolledContent content) {
            // Only the arrow leaves: h scrolls, as j and k move, without going to another panel.
            boolean leaving =
                    event.code() == KeyCode.ESCAPE || (focused && event.matches(Actions.MOVE_LEFT) && column == 0);
            // Escape, and Left where it leaves as Escape does, drop an open range before they leave the area (ADR 60).
            return cursor.filter(on -> leaving && focused && on.mark().isDefined())
                    .map(ranged -> moved(ranged.marked().get(), content))
                    // Key handlers also see keys typed elsewhere; the callback checks that an area has focus.
                    .orElse(() -> Option.when(leaving && leaveScrollArea.getAsBoolean(), EventResult.HANDLED))
                    .orElse(() -> Option.when(!focused, EventResult.UNHANDLED))
                    .orElse(() -> cursor.flatMap(on -> move(on, event)).map(to -> moved(to, content)))
                    .orElse(() -> cursor.flatMap(on -> KEYMAP.handle(
                                    review ? Scopes.PULL_REQUEST_DIFF : Scopes.DIFF, event, Option.of(at(on, content))))
                            .map(this::reviewed))
                    .getOrElse(() -> scroll(event) ? scrolled(content) : EventResult.UNHANDLED);
        }

        /**
         * Sends {@code msg}, which a key of the diff of a pull request made, telling the area first where it scrolls
         * to if it does.
         */
        private EventResult reviewed(Msg msg) {
            if (msg instanceof ViewMsg.ContentScrolled(var ignored, Scrolled to)) {
                told(to);
            }
            dispatch.accept(msg);
            return EventResult.HANDLED;
        }

        /**
         * Where {@code cursor} is, as the keys of the diff of a pull request act on it, with where the keys that move
         * it take it.
         */
        private DiffCursorAt at(DiffCursor cursor, ScrolledContent content) {
            Anchor acting = acting(cursor);
            return new DiffCursorAt(
                    area.id(),
                    new Scrolled(content, cursor.row(), column, Option.of(acting), cursor.markAnchor()),
                    acting,
                    cursor.nextThread().map(next -> movedTo(next, content)),
                    cursor.previousThread().map(previous -> movedTo(previous, content)),
                    cursor.marked().map(marked -> movedTo(marked, content)));
        }

        /**
         * What a key acts on with the cursor on {@code cursor}: the stop it is on, but on the bar of a version
         * rendered, which is its only stop, the first line of the block at the top of the area, so a version shown
         * as source again has the cursor there (ADR 103).
         */
        private Anchor acting(DiffCursor cursor) {
            return cursor.anchor() instanceof Anchor.File
                    ? styled.flatMap(shown -> shown.top(cursor.row())).getOrElse(cursor::anchor)
                    : cursor.anchor();
        }

        private Msg movedTo(DiffCursor cursor, ScrolledContent content) {
            return new ViewMsg.ContentScrolled(area.id(), scrolledTo(cursor, content));
        }

        private Scrolled scrolledTo(DiffCursor cursor, ScrolledContent content) {
            return new Scrolled(content, cursor.row(), column, Option.of(cursor.anchor()), cursor.markAnchor());
        }

        /**
         * Where the keys that move the cursor of a rich diff take it, if {@code event} is one of them.
         */
        private static Option<DiffCursor> move(DiffCursor cursor, KeyEvent event) {
            return Option.<DiffCursor>none()
                    .orElse(Option.when(event.matches(Actions.MOVE_UP) || event.isChar(UP), cursor::up))
                    .orElse(Option.when(event.matches(Actions.MOVE_DOWN) || event.isChar(DOWN), cursor::down))
                    .orElse(Option.when(event.matches(Actions.PAGE_UP), cursor::pageUp))
                    .orElse(Option.when(event.matches(Actions.PAGE_DOWN), cursor::pageDown))
                    .orElse(Option.when(event.matches(Actions.HOME), cursor::home))
                    .orElse(Option.when(event.matches(Actions.END), cursor::end));
        }

        /**
         * Shows the cursor {@code moved}, and tells the model where it is and where the area is scrolled to, in one
         * message, so the model never has the cursor off the screen.
         */
        private EventResult moved(DiffCursor moved, ScrolledContent content) {
            cursor = Option.of(moved);
            RichTextState state = element.state();
            state.scrollToTop();
            state.scrollDown(moved.row());
            return scrolled(content);
        }

        /**
         * A click gives the focus to an area, even from the panes, and puts the cursor of a rich diff
         * on the line clicked; the wheel scrolls.
         */
        private EventResult mouse(MouseEvent event, ScrolledContent content) {
            RichTextState state = element.state();
            if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
                focus.accept(id);
                // The text starts a line below the top of the area, under its border.
                return cursor.flatMap(on -> Option.of(element.renderedArea())
                                .flatMap(drawn -> on.clicked(event.y() - drawn.top() - 1)))
                        .map(clicked -> {
                            EventResult result = moved(clicked, content);
                            // A click on a marker shows all the lines it leaves out, as Enter on it does (ADR 77).
                            if (clicked.anchor() instanceof Anchor.Gap) {
                                KEYMAP.handle(
                                                Scopes.DIFF,
                                                KeyEvent.ofKey(KeyCode.ENTER),
                                                Option.of(at(clicked, content)))
                                        .forEach(dispatch);
                            }
                            return result;
                        })
                        .getOrElse(EventResult.HANDLED);
            }
            if (event.kind() == MouseEventKind.SCROLL_UP) {
                return cursor.map(on -> moved(on.scrolled(-WHEEL_LINES), content))
                        .getOrElse(() -> {
                            state.scrollUp(WHEEL_LINES);
                            return scrolled(content);
                        });
            }
            if (event.kind() == MouseEventKind.SCROLL_DOWN) {
                return cursor.map(on -> moved(on.scrolled(WHEEL_LINES), content))
                        .getOrElse(() -> {
                            state.scrollDown(WHEEL_LINES);
                            return scrolled(content);
                        });
            }
            return EventResult.UNHANDLED;
        }

        /**
         * Scrolls the area as it does on {@code event}, and says whether it was a key for that: up and down its
         * element, sideways itself.
         */
        private boolean scroll(KeyEvent event) {
            RichTextState state = element.state();
            return Option.<Runnable>none()
                    .orElse(Option.when(event.matches(Actions.MOVE_UP), () -> state::scrollUp))
                    .orElse(Option.when(event.matches(Actions.MOVE_DOWN), () -> state::scrollDown))
                    .orElse(Option.when(event.matches(Actions.PAGE_UP), () -> state::pageUp))
                    .orElse(Option.when(event.matches(Actions.PAGE_DOWN), () -> state::pageDown))
                    .orElse(Option.when(event.matches(Actions.HOME), () -> state::scrollToTop))
                    .orElse(Option.when(event.matches(Actions.END), () -> state::scrollToBottom))
                    .orElse(Option.when(left(event), () -> () -> column = Math.max(0, column - 1)))
                    .orElse(Option.when(right(event), () -> () -> column = Math.min(columns, column + 1)))
                    .peek(Runnable::run)
                    .isDefined();
        }

        private static boolean left(KeyEvent event) {
            return event.matches(Actions.MOVE_LEFT) || event.isChar(LEFT);
        }

        private static boolean right(KeyEvent event) {
            return event.matches(Actions.MOVE_RIGHT) || event.isChar(RIGHT);
        }

        /**
         * Tells the model where the area showing {@code content} is scrolled to now.
         */
        private EventResult scrolled(ScrolledContent content) {
            RichTextState state = element.state();
            Scrolled to = new Scrolled(
                    content,
                    state.scrollRow(),
                    column,
                    cursor.map(DiffCursor::anchor),
                    cursor.flatMap(DiffCursor::markAnchor));
            told(to);
            dispatch.accept(new ViewMsg.ContentScrolled(area.id(), to));
            return EventResult.HANDLED;
        }

        /**
         * Remembers that the model was told the area is now at {@code to}.
         */
        private void told(Scrolled to) {
            sent = Option.of(Tuple.of(sent.map(Tuple2::_1).getOrElse(shownAt), to));
        }

        /**
         * The text area {@code fitted}, its lines fitted to the size it was last drawn in, drawn so they fit the size
         * it is drawn in (ADR 80). The text area only learns its size while it draws, so a frame that draws it in
         * another size, like one after the panes are resized, draws it once to let it learn it, puts back the cells
         * that drew, and draws it {@code again}, fitted anew. Drawn twice, it is still given the events and the focus
         * once, as TamboUI registers an element and its id once per frame.
         */
        private final class Fitted implements Element {

            private final RichTextAreaElement fitted;
            private final Supplier<RichTextAreaElement> again;

            Fitted(RichTextAreaElement fitted, Supplier<RichTextAreaElement> again) {
                this.fitted = fitted;
                this.again = again;
            }

            @Override
            public void render(Frame frame, Rect at, RenderContext context) {
                if (at.width() == drawnWidth && at.height() == drawnHeight) {
                    fitted.render(frame, at, context);
                    return;
                }
                drawnWidth = at.width();
                drawnHeight = at.height();
                Buffer buffer = frame.buffer();
                Rect cells = buffer.area().intersection(at);
                Vector<Cell> before = Vector.range(cells.top(), cells.bottom())
                        .flatMap(y -> Vector.range(cells.left(), cells.right()).map(x -> buffer.get(x, y)));
                fitted.render(frame, at, context);
                before.forEachWithIndex((cell, index) ->
                        buffer.set(cells.left() + index % cells.width(), cells.top() + index / cells.width(), cell));
                again.get().render(frame, at, context);
            }

            @Override
            public Constraint constraint() {
                return fitted.constraint();
            }

            @Override
            public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
                return fitted.preferredSize(availableWidth, availableHeight, context);
            }
        }

        /**
         * {@code body}, styled in {@code theme} as {@code lines}, each drawn when it shows ({@link Rows}), with where
         * the stops of its cursor start, if it has one.
         *
         * @param asGiven the hunk shown as git gives it, as a range is open in it (ADR 83)
         * @param indexes the index of the stop of each anchor, {@link DiffCursor#indexes}
         * @param widest  how wide the widest of {@code lines} is, which the tint of the cursor never makes wider
         * @param filling the lines drawn to the edge of the area (ADR 80): the bar of each file, the lines of the diff
         *                and the markers of the lines it leaves out, not the threads on them
         * @param places  where each line of a version rendered is drawn, in order (ADR 103)
         * @param width   how wide a version rendered was drawn
         */
        private record Styled(
                Body body,
                ThemeName theme,
                Option<DiffStops.HunkAt> asGiven,
                Rows lines,
                Vector<RichDiff.Stop> stops,
                Map<Anchor, Integer> indexes,
                int widest,
                Set<Integer> filling,
                Vector<RichDiff.Place> places,
                int width) {

            static Styled of(
                    Body body, ThemeName theme, Option<DiffStops.HunkAt> asGiven, RichDiff.Drawn drawn, int width) {
                Rows lines = drawn.rows();
                Vector<RichDiff.Stop> stops = drawn.stops().toVector();
                return new Styled(
                        body,
                        theme,
                        asGiven,
                        lines,
                        stops,
                        DiffCursor.indexes(stops),
                        // The render thread waits for it, as it does for the lines to be drawn.
                        lines.widest(),
                        stops.filter(stop -> switch (stop.mark().anchor()) {
                                    case Anchor.File _, Anchor.OnLine _, Anchor.Gap _ -> true;
                                    case Anchor.Lead _, Anchor.Thread _, Anchor.Comment _ -> false;
                                })
                                .map(RichDiff.Stop::row)
                                .toSet(),
                        drawn.places().toVector(),
                        width);
            }

            /**
             * This body with {@code kept}, its lines with others kept drawn.
             */
            Styled withLines(Rows kept) {
                return new Styled(body, theme, asGiven, kept, stops, indexes, widest, filling, places, width);
            }

            /**
             * The hunk of the line {@code anchor}, if it is a line of this body.
             */
            Option<DiffStops.HunkAt> hunk(Anchor anchor) {
                return indexes.get(anchor)
                        .flatMap(index -> stops.get(index).mark().hunk());
            }

            /**
             * The line the stop {@code anchor} starts on, if it is a stop of this body.
             */
            Option<Integer> row(Anchor anchor) {
                return indexes.get(anchor)
                        .map(index -> stops.get(index).row())
                        .orElse(() -> places.find(place -> place.anchor().equals(anchor))
                                .map(RichDiff.Place::row));
            }

            /**
             * The first line of the block of a version rendered at the top of the area scrolled to {@code row}, or of
             * its first block when none is at or above it; none for a body that is no version rendered (ADR 103).
             */
            Option<Anchor> top(int row) {
                return places.filter(place -> place.row() <= row)
                        .lastOption()
                        // Above the first block, as with the bar of the file at the top, the first block.
                        .orElse(places.headOption())
                        .flatMap(last -> places.find(place -> place.row() == last.row()))
                        .map(RichDiff.Place::anchor);
            }
        }
    }

    /**
     * How a body is drawn: with the stops of its cursor, the hunk a range is open in as git gives it (ADR 83), and a
     * version of a file rendered as wide as its area (ADR 103).
     */
    @FunctionalInterface
    interface Styler {

        RichDiff.Drawn drawn(Body body, Option<DiffStops.HunkAt> asGiven, int width);
    }
}
