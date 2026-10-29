package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.RichTextState;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Scrolled;
import net.vanfleteren.jazygit.state.ScrolledContent;
import net.vanfleteren.jazygit.ui.ContentView.Body;
import net.vanfleteren.jazygit.ui.ContentView.Shown;
import net.vanfleteren.jazygit.ui.ContentView.Tone;

/**
 * The right-hand content panel: it draws what {@link ContentView} says it shows, styling the diffs, the log, the
 * commits, the stashes and the pull requests, and keeps the areas that scroll through them.
 */
public final class ContentPanel {

    private static final String MESSAGE_INDENT = "    ";
    /**
     * How the header lines of a diff start, before its first hunk.
     */
    private static final List<String> DIFF_HEADER_PREFIXES = List.of("+++", "---", "diff ", "index ");

    private final Theme.SpanStyles styles;
    // The scrollable areas for the diffs, the branch log and the commit. Where they are scrolled to lives in the
    // model; the elements are kept between renders as they also lay out the text for the scrollbar. Only used from
    // the render thread.
    private final Map<ContentArea, ScrollArea> areas;

    /**
     * @param styles          how the diffs and the log are colored
     * @param leaveScrollArea called when Escape is pressed; gives the focus back to the pane if a focusable
     *                        area had it, and says whether it did
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
     * The unified diff as one styled text, so it is a single element to lay out however long the diff is.
     */
    static Text diffText(String diff, Theme.SpanStyles styles) {
        return styledText(lines(diff).map(line -> diffLine(line, styles)));
    }

    /**
     * The branch log, each commit as {@link #logLines} lists it: the {@code commit <sha>} part of each commit's first line is
     * yellow. The commits on the base are dimmed instead, and the fork point is marked after its sha.
     */
    static Text logText(List<Commit> commits, ZoneId zone, AheadView ahead, Theme.SpanStyles styles) {
        DateTimeFormatter dateTime = dateTime(zone);
        return styledText(CommitGraph.layout(commits).flatMap(node -> {
            Sha sha = node.commit().sha();
            List<String> lines = logLines(node, dateTime, ahead.mark(sha));
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
        return CommitGraph.layout(commits).flatMap(node -> logLines(node, dateTime, Option.none()));
    }

    /**
     * The lines of the commit of {@code node} in {@link #logLines(List, ZoneId)}, with {@code mark} after its sha.
     */
    private static List<String> logLines(CommitGraph.Node node, DateTimeFormatter dateTime, Option<String> mark) {
        Commit c = node.commit();
        List<String> lines = List.of(
                        Messages.get("content.commitSha", c.shortSha())
                                + mark.map(text -> "  " + text).getOrElse(""),
                        Messages.get("content.author", c.authorName(), c.authorEmail()),
                        dateTime.format(c.authorTime()),
                        "")
                .appendAll(messageLines(c))
                .append("");
        return LogGraph.gutter(node)
                .draw(
                        lines,
                        Messages.get(
                                "content.merge", c.parents().map(Sha::shortSha).mkString(" ")));
    }

    /**
     * How the log shows when a commit was made, in {@code zone}.
     */
    private static DateTimeFormatter dateTime(ZoneId zone) {
        return DateTimeFormatter.ofPattern(Messages.get("content.dateTimePattern"))
                .withZone(zone);
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
        DateTimeFormatter date = DateTimeFormatter.ofPattern(Messages.get("content.commitDatePattern"), Locale.ENGLISH);
        return Span.raw(Messages.get("content.commitDate", date.format(time.atZone(ZoneId.systemDefault()))));
    }

    /**
     * Like {@code git show}: the commit's header, its indented message, a {@code ---} line and then the changes.
     */
    static Text commitText(Commit commit, String changes, DiffStyle style, Theme.SpanStyles styles) {
        List<String> message = messageLines(commit);
        return styledText(List.of(
                        styled(Messages.get("content.commitSha", commit.sha().value()), Theme.Classes.SHA, styles),
                        Span.raw(Messages.get("content.commitAuthor", commit.authorName(), commit.authorEmail())),
                        dateLine(commit.authorTime()),
                        Span.raw(""),
                        styled(message.head(), Theme.Classes.BOLD, styles))
                .map(Line::from)
                .appendAll(message.tail().map(line -> Line.from(Span.raw(line))))
                .append(Line.from(Span.raw("---")))
                .appendAll(changeLines(changes, style, styles)));
    }

    /**
     * Like {@link #commitText}: the stash's name and message, the branch it was stashed on if its message says, when
     * it was stashed, a {@code ---} line and then the changes.
     */
    static Text stashText(Stash stash, String changes, DiffStyle style, Theme.SpanStyles styles) {
        return styledText(List.of(styled(
                        Messages.get("content.stashHeader", stash.ref(), stash.message()), Theme.Classes.SHA, styles))
                .appendAll(stash.branch().map(branch -> Span.raw(Messages.get("content.stashBranch", branch.value()))))
                .append(dateLine(stash.time()))
                .map(Line::from)
                .append(Line.from(Span.raw("---")))
                .appendAll(changeLines(changes, style, styles)));
    }

    /**
     * The changes of a commit or a stash, a unified diff, drawn in {@code style}.
     */
    static List<Line> changeLines(String changes, DiffStyle style, Theme.SpanStyles styles) {
        return switch (style) {
            case PLAIN -> lines(changes).map(line -> diffLine(line, styles));
            case RICH -> RichDiff.lines(changes, styles);
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
     * The pane that gets the focus back when Escape is pressed in the area with id {@code areaId}, if that
     * is a focusable area: the pane whose highlighted row it shows.
     */
    public Option<String> paneOfArea(Model model, String areaId) {
        return ContentArea.focusable(areaId).map(area -> area.pane(model));
    }

    /**
     * The id of the area that shows what the pane {@code paneId} has highlighted, to move the focus into:
     * the staged diff if it shows anything, else the unstaged one, or the commit; none while it shows nothing.
     */
    public Option<String> areaOfPane(Model model, String paneId) {
        return ContentView.of(model, paneId)
                .areas()
                .map(Shown.Area::area)
                .find(area -> area.focusable() && area.pane(model).equals(paneId))
                .map(ContentArea::id);
    }

    /**
     * What goes with the pane that has the focus, {@code focusedId}; while a popup has it, what goes with the
     * pane the popup was opened from. That holds also in the frame a popup closes in, as it still has the focus
     * until the focus is given back.
     */
    public StyledElement<?> render(Model model, String focusedId) {
        tabbableWhileIn(focusedId);
        return element(ContentView.of(model, focusedId), model, focusedId);
    }

    /**
     * How {@code shown} is drawn: a message in a panel, or the areas, where the model has them scrolled to.
     */
    private StyledElement<?> element(Shown shown, Model model, String focusedId) {
        return switch (shown) {
            case Shown.Message(String title, String content, Tone tone) ->
                panel(
                        title,
                        switch (tone) {
                            case DIM -> text(content).addClass(Theme.Classes.DIM);
                            case ERROR -> text(content).addClass(Theme.Classes.ERROR);
                        });
            case Shown.Area area -> area(area.area()).show(model, focusedId, area, this::bodyText);
            case Shown.Stacked(List<Shown.Area> stacked) ->
                column(stacked.<Element>map(area -> element(area, model, focusedId))
                        .toJavaArray(Element[]::new));
        };
    }

    /**
     * {@code body}, styled.
     */
    private Text bodyText(Body body) {
        return switch (body) {
            case Body.Diff(String diff) -> diffText(diff, styles);
            case Body.BranchLog(List<Commit> commits, AheadView ahead) ->
                logText(commits, ZoneId.systemDefault(), ahead, styles);
            case Body.CommitChanges(Commit commit, String changes, DiffStyle style) ->
                commitText(commit, changes, style, styles);
            case Body.StashChanges(Stash stash, String changes, DiffStyle style) ->
                stashText(stash, changes, style, styles);
            case Body.FileChanges(String changes, DiffStyle style) -> styledText(changeLines(changes, style, styles));
            case Body.PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) ->
                pullRequestText(pullRequest, preview, styles);
        };
    }

    /**
     * Lets Tab move between the focusable areas only while one of them, {@code focusedId}, has the focus.
     */
    private void tabbableWhileIn(String focusedId) {
        boolean inContent = isArea(focusedId);
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
        DateTimeFormatter updated =
                DateTimeFormatter.ofPattern(Messages.get("content.commitDatePattern"), Locale.ENGLISH);
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
                                pr.headBranch(),
                                preview.base())),
                        Span.raw(Messages.get(
                                "content.pullRequest.updated",
                                updated.format(pr.updatedAt().atZone(ZoneId.systemDefault())))),
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

        private final ContentArea area;
        private final String id;
        private final RichTextAreaElement element;
        private final BooleanSupplier leaveScrollArea;
        private final Consumer<String> focus;
        private final Consumer<Msg> dispatch;
        // The body last shown, the theme it was styled in and its text: a frame that shows the same, like one drawn
        // only to turn a spinner, does not style it again.
        private Option<Styled> styled = Option.none();

        ScrollArea(ContentArea area, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
            this.area = area;
            this.id = area.id();
            this.leaveScrollArea = leaveScrollArea;
            this.focus = focus;
            this.dispatch = dispatch;
            element = richTextArea().id(id).rounded().scrollbar().fill();
        }

        /**
         * Makes the area part of the Tab cycle while {@code inContent}, so Tab goes from one area to the
         * other then, but from the last pane back to the first otherwise. Only the areas with a pane are.
         */
        void tabbable(boolean inContent) {
            element.focusable(area.focusable() && inContent);
        }

        /**
         * The area as {@code shown}, its body as {@code style} styles it, where the model has it scrolled to.
         */
        RichTextAreaElement show(Model model, String focusedId, Shown.Area shown, Function<Body, Text> style) {
            ThemeName theme = model.preferences().theme();
            Styled current = styled.filter(last -> last.body().equals(shown.body()) && last.theme() == theme)
                    .getOrElse(() -> new Styled(shown.body(), theme, style.apply(shown.body())));
            styled = Option.of(current);
            ScrolledContent content = shown.content();
            Scrolled at = model.scrolled(id, content);
            RichTextState state =
                    element.text(current.text()).title(shown.title()).state();
            state.scrollToTop();
            state.scrollDown(at.row());
            state.scrollRight(at.column());
            boolean focused = id.equals(focusedId);
            return element.onKeyEvent(event -> keys(event, focused, content))
                    .onMouseEvent(event -> mouse(event, content));
        }

        private EventResult keys(KeyEvent event, boolean focused, ScrolledContent content) {
            // Key handlers also see keys typed elsewhere; the callback checks that an area has focus.
            if (event.code() == KeyCode.ESCAPE && leaveScrollArea.getAsBoolean()) {
                return EventResult.HANDLED;
            }
            return focused && scroll(element.state(), event) ? scrolled(content) : EventResult.UNHANDLED;
        }

        /**
         * A click gives the focus to an area that can have it, even from the panes; the wheel scrolls.
         */
        private EventResult mouse(MouseEvent event, ScrolledContent content) {
            RichTextState state = element.state();
            if (event.kind() == MouseEventKind.PRESS && event.isLeftButton() && area.focusable()) {
                focus.accept(id);
                return EventResult.HANDLED;
            }
            if (event.kind() == MouseEventKind.SCROLL_UP) {
                state.scrollUp(WHEEL_LINES);
                return scrolled(content);
            }
            if (event.kind() == MouseEventKind.SCROLL_DOWN) {
                state.scrollDown(WHEEL_LINES);
                return scrolled(content);
            }
            return EventResult.UNHANDLED;
        }

        /**
         * Scrolls {@code state} as the area itself does on {@code event}, and says whether it was a key for that.
         */
        private static boolean scroll(RichTextState state, KeyEvent event) {
            return Option.<Runnable>none()
                    .orElse(Option.when(event.matches(Actions.MOVE_UP), () -> state::scrollUp))
                    .orElse(Option.when(event.matches(Actions.MOVE_DOWN), () -> state::scrollDown))
                    .orElse(Option.when(event.matches(Actions.PAGE_UP), () -> state::pageUp))
                    .orElse(Option.when(event.matches(Actions.PAGE_DOWN), () -> state::pageDown))
                    .orElse(Option.when(event.matches(Actions.HOME), () -> state::scrollToTop))
                    .orElse(Option.when(event.matches(Actions.END), () -> state::scrollToBottom))
                    .orElse(Option.when(event.matches(Actions.MOVE_LEFT), () -> state::scrollLeft))
                    .orElse(Option.when(event.matches(Actions.MOVE_RIGHT), () -> state::scrollRight))
                    .peek(Runnable::run)
                    .isDefined();
        }

        /**
         * Tells the model where the area showing {@code content} is scrolled to now.
         */
        private EventResult scrolled(ScrolledContent content) {
            RichTextState state = element.state();
            dispatch.accept(new Msg.ContentScrolled(id, new Scrolled(content, state.scrollRow(), state.scrollCol())));
            return EventResult.HANDLED;
        }

        /**
         * {@code body}, styled in {@code theme} as {@code text}.
         */
        private record Styled(Body body, ThemeName theme, Text text) {}
    }
}
