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
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
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
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Scrolled;
import net.vanfleteren.jazygit.state.ScrolledContent;
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
            case RICH -> RichDiff.drawn(diff.parsed(), look.syntax(), look.words(), remarks, styles);
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
        List<String> message = messageLines(commit);
        return changeDrawn(changes, look, styles)
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
        return changeDrawn(changes, look, styles)
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
        return changeDrawn(changes, look, styles).lines();
    }

    /**
     * A unified diff, drawn as {@code look} says, with the stops of the cursor in the rich view (ADR 60).
     */
    private static RichDiff.Drawn changeDrawn(ShownDiff changes, DiffLook look, Theme.SpanStyles styles) {
        return switch (look.style()) {
            case PLAIN -> RichDiff.Drawn.plain(lines(changes.text()).map(line -> diffLine(line, styles)));
            case RICH -> RichDiff.drawn(changes.parsed(), look.syntax(), look.words(), Option.none(), styles);
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
        tabbableWhileIn(focusedId, model);
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
            case Shown.Area area -> area(area.area()).show(model, focusedId, area, this::bodyDrawn, styles);
            case Shown.Stacked(List<Shown.Area> stacked) ->
                column(stacked.<Element>map(area -> element(area, model, focusedId))
                        .toJavaArray(Element[]::new));
        };
    }

    /**
     * {@code body}, styled, with the stops of the cursor of a rich diff (ADR 60).
     */
    private RichDiff.Drawn bodyDrawn(Body body) {
        return switch (body) {
            case Body.Diff(ShownDiff diff, DiffLook look, var remarks) -> diffDrawn(diff, look, remarks, styles);
            case Body.BranchLog(List<Commit> commits, AheadView ahead) ->
                plain(logText(commits, ZoneId.systemDefault(), ahead, styles));
            case Body.CommitChanges(Commit commit, ShownDiff changes, DiffLook look) ->
                commitDrawn(commit, changes, look, styles);
            case Body.StashChanges(Stash stash, ShownDiff changes, DiffLook look) ->
                stashDrawn(stash, changes, look, styles);
            case Body.PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) ->
                plain(pullRequestText(pullRequest, preview, styles));
        };
    }

    private static RichDiff.Drawn plain(Text text) {
        return RichDiff.Drawn.plain(List.ofAll(text.lines()));
    }

    /**
     * Lets Tab move between the focusable areas only while one of them, {@code focusedId}, has the focus, or had it
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
                                pr.headBranch(),
                                preview.base())),
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

        ScrollArea(ContentArea area, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
            this.area = area;
            this.id = area.id();
            this.leaveScrollArea = leaveScrollArea;
            this.focus = focus;
            this.dispatch = dispatch;
            element = richTextArea().id(id).scrollbar().fill();
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
        RichTextAreaElement show(
                Model model,
                String focusedId,
                Shown.Area shown,
                Function<Body, RichDiff.Drawn> style,
                Theme.SpanStyles styles) {
            ThemeName theme = model.preferences().theme();
            Styled current = styled.filter(last -> last.body().equals(shown.body()) && last.theme() == theme)
                    .getOrElse(() -> Styled.of(shown.body(), theme, style.apply(shown.body())));
            styled = Option.of(current);
            ScrolledContent content = shown.content();
            Scrolled known = model.scrolled(id, content);
            sent = sent.filter(told -> told._1().equals(known));
            Scrolled at = sent.map(Tuple2::_2).getOrElse(known);
            shownAt = known;
            RichTextState state = element.state();
            cursor = DiffCursor.of(
                    current.stops(), current.text().height(), state.viewportHeight(), at.row(), at.cursor(), at.mark());
            review = shown.body() instanceof Body.Diff(var diff, var look, var remarks) && remarks.isDefined();
            boolean focused = id.equals(focusedId);
            Text text = cursor.filter(ignored -> focused)
                    .map(on -> on.tinted(current.text(), styles.of(Theme.Classes.DIFF_CURSOR)))
                    .getOrElse(current.text());
            // The width of the area is that of the frame before, as the element only learns it while it draws.
            columns =
                    Math.max(0, List.ofAll(text.lines()).map(Line::width).max().getOrElse(0) - state.viewportWidth());
            column = Math.clamp(at.column(), 0, columns);
            element.text(column == 0 ? text : sideways(text, column)).title(shown.title());
            state.scrollToTop();
            state.scrollDown(cursor.map(DiffCursor::row).getOrElse(at.row()));
            boolean ofFiles = area.pane(model).equals(FilesPanel.ID);
            return element.onKeyEvent(event -> keys(event, focused, ofFiles, content))
                    .onMouseEvent(event -> mouse(event, content));
        }

        /**
         * @param ofFiles whether the area shows a diff of the files pane, which Left leaves as Escape does, once it
         *                is scrolled all the way left: Right in the files pane moves into it
         */
        private EventResult keys(KeyEvent event, boolean focused, boolean ofFiles, ScrolledContent content) {
            // Only the arrow leaves: h scrolls, as j and k move, without going to another panel.
            boolean leaving = event.code() == KeyCode.ESCAPE
                    || (focused && ofFiles && event.matches(Actions.MOVE_LEFT) && column == 0);
            // Escape, and Left where it leaves as Escape does, drop an open range before they leave the area (ADR 60).
            return cursor.filter(on -> leaving && focused && on.mark().isDefined())
                    .map(ranged -> moved(ranged.marked().get(), content))
                    // Key handlers also see keys typed elsewhere; the callback checks that an area has focus.
                    .orElse(() -> Option.when(leaving && leaveScrollArea.getAsBoolean(), EventResult.HANDLED))
                    .orElse(() -> Option.when(!focused, EventResult.UNHANDLED))
                    .orElse(() -> cursor.flatMap(on -> move(on, event)).map(to -> moved(to, content)))
                    .orElse(() -> cursor.filter(ignored -> review)
                            .flatMap(on -> KEYMAP.handle(Scopes.PULL_REQUEST_DIFF, event, Option.of(at(on, content))))
                            .map(this::reviewed))
                    .getOrElse(() -> scroll(event) ? scrolled(content) : EventResult.UNHANDLED);
        }

        /**
         * Sends {@code msg}, which a key of the diff of a pull request made, telling the area first where it scrolls
         * to if it does.
         */
        private EventResult reviewed(Msg msg) {
            if (msg instanceof Msg.ContentScrolled(var ignored, Scrolled to)) {
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
            return new DiffCursorAt(
                    id,
                    scrolledTo(cursor, content),
                    cursor.anchor(),
                    cursor.nextThread().map(next -> movedTo(next, content)),
                    cursor.previousThread().map(previous -> movedTo(previous, content)),
                    cursor.marked().map(marked -> movedTo(marked, content)));
        }

        private Msg movedTo(DiffCursor cursor, ScrolledContent content) {
            return new Msg.ContentScrolled(id, scrolledTo(cursor, content));
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
         * A click gives the focus to an area that can have it, even from the panes, and puts the cursor of a rich diff
         * on the line clicked; the wheel scrolls.
         */
        private EventResult mouse(MouseEvent event, ScrolledContent content) {
            RichTextState state = element.state();
            if (event.kind() == MouseEventKind.PRESS && event.isLeftButton() && area.focusable()) {
                focus.accept(id);
                // The text starts a line below the top of the area, under its border.
                return cursor.flatMap(on -> Option.of(element.renderedArea())
                                .flatMap(drawn -> on.clicked(event.y() - drawn.top() - 1)))
                        .map(clicked -> moved(clicked, content))
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
         * {@code text} scrolled {@code column} columns sideways: each of its lines without its first columns.
         */
        private static Text sideways(Text text, int column) {
            return Text.from(List.ofAll(text.lines())
                    .map(line -> Line.from(List.ofAll(line.spans())
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
                            .toJavaList()))
                    .toJavaList());
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
            dispatch.accept(new Msg.ContentScrolled(id, to));
            return EventResult.HANDLED;
        }

        /**
         * Remembers that the model was told the area is now at {@code to}.
         */
        private void told(Scrolled to) {
            sent = Option.of(Tuple.of(sent.map(Tuple2::_1).getOrElse(shownAt), to));
        }

        /**
         * {@code body}, styled in {@code theme} as {@code text}, with where the stops of its cursor start, if it has
         * one.
         */
        private record Styled(Body body, ThemeName theme, Text text, Vector<RichDiff.Stop> stops) {

            static Styled of(Body body, ThemeName theme, RichDiff.Drawn drawn) {
                return new Styled(
                        body, theme, styledText(drawn.lines()), drawn.stops().toVector());
            }
        }
    }
}
