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
import io.vavr.collection.List;
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
import net.vanfleteren.jazygit.state.AheadUpdate;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.DivergenceView;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.Scrolled;
import net.vanfleteren.jazygit.state.ScrolledContent;

/**
 * The right-hand content panel: it always mirrors whichever left pane currently has focus,
 * showing the file diff, the branch log, the commit diff/body or what a stash stashed; on the pull requests screen,
 * the highlighted pull request.
 */
public final class ContentPanel {

    private static final String COMMIT_AREA_ID = "commit-changes";
    private static final String STASH_AREA_ID = "stash-changes";
    private static final String DIVERGENCE_AREA_ID = "divergence-commit";
    private static final String PULL_REQUEST_AREA_ID = "pull-request-preview";
    private static final List<String> LEFT_PANES =
            List.of(FilesPanel.ID, BranchesPanel.ID, CommitsPanel.ID, StashPanel.ID);
    private static final String MESSAGE_INDENT = "    ";
    /**
     * How the header lines of a diff start, before its first hunk.
     */
    private static final List<String> DIFF_HEADER_PREFIXES = List.of("+++", "---", "diff ", "index ");

    private final Theme.SpanStyles styles;
    // The scrollable areas for the diffs, the branch log and the commit. Where they are scrolled to lives in the
    // model; the elements are kept between renders as they also lay out the text for the scrollbar. Only used from
    // the render thread.
    private final ScrollArea stagedArea;
    private final ScrollArea unstagedArea;
    private final ScrollArea commitArea;
    private final ScrollArea stashArea;
    // The commit highlighted in the divergence the branches pane lists.
    private final ScrollArea divergenceArea;
    // Not focusable: clicking the log must not take the focus from the branches pane it mirrors.
    private final ScrollArea logArea;
    private final ScrollArea pullRequestArea;
    private final List<ScrollArea> focusableAreas;

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
        Function<String, ScrollArea> area = id -> new ScrollArea(id, leaveScrollArea, focus, dispatch);
        stagedArea = area.apply("diff-staged");
        unstagedArea = area.apply("diff-unstaged");
        commitArea = area.apply(COMMIT_AREA_ID);
        stashArea = area.apply(STASH_AREA_ID);
        divergenceArea = area.apply(DIVERGENCE_AREA_ID);
        logArea = area.apply("branch-log");
        pullRequestArea = area.apply(PULL_REQUEST_AREA_ID);
        focusableAreas = List.of(stagedArea, unstagedArea, commitArea, stashArea, divergenceArea, pullRequestArea);
    }

    /**
     * Whether {@code id} is an area of the content panel that can have the focus, rather than a pane.
     */
    public static boolean isArea(String id) {
        return List.of(
                        "diff-staged",
                        "diff-unstaged",
                        COMMIT_AREA_ID,
                        STASH_AREA_ID,
                        DIVERGENCE_AREA_ID,
                        PULL_REQUEST_AREA_ID)
                .contains(id);
    }

    /**
     * The text of {@code diff}, with a line saying it is truncated when it was cut off.
     */
    static String shown(DiffText diff) {
        return diff.truncated() ? diff.text() + Messages.get("git.diff.truncated") + "\n" : diff.text();
    }

    /**
     * What a commit changed as it is shown: when changes to whitespace only are left out and nothing else
     * changed, a line that says so.
     */
    static String shownChanges(DiffText changes, DiffOptions options) {
        return changes.isBlank() && options.ignoreWhitespace()
                ? Messages.get("content.onlyWhitespace") + "\n"
                : shown(changes);
    }

    /**
     * {@code title}, saying that changes to whitespace only are left out when they are.
     */
    static String titled(String title, DiffOptions options) {
        return options.ignoreWhitespace() ? Messages.get("panel.ignoringWhitespace", title) : title;
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

    private static Option<Commit> commit(Model model, Sha sha) {
        return model.commit(sha);
    }

    private static Option<Stash> stash(Model model, Sha sha) {
        return model.repo()
                .stashes()
                .toOption()
                .flatMap(stashes -> stashes.find(stash -> stash.sha().equals(sha)));
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
    static Text commitText(Commit commit, String changes, Theme.SpanStyles styles) {
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
                .appendAll(lines(changes).map(line -> diffLine(line, styles))));
    }

    /**
     * Like {@link #commitText}: the stash's name and message, the branch it was stashed on if its message says, when
     * it was stashed, a {@code ---} line and then the changes.
     */
    static Text stashText(Stash stash, String changes, Theme.SpanStyles styles) {
        return styledText(List.of(styled(
                        Messages.get("content.stashHeader", stash.ref(), stash.message()), Theme.Classes.SHA, styles))
                .appendAll(stash.branch().map(branch -> Span.raw(Messages.get("content.stashBranch", branch))))
                .append(dateLine(stash.time()))
                .map(Line::from)
                .append(Line.from(Span.raw("---")))
                .appendAll(lines(changes).map(line -> diffLine(line, styles))));
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

    private static StyledElement<?> loadingPanel(String title) {
        return dimPanel(title, Placeholders.loading());
    }

    private static StyledElement<?> dimPanel(String title, String content) {
        return panel(title, text(content).addClass(Theme.Classes.DIM));
    }

    private static <T> StyledElement<?> whenLoaded(
            String title, Loadable<T> loadable, Function<T, StyledElement<?>> view) {
        return switch (loadable) {
            case Loadable.Loaded<T>(T value) -> view.apply(value);
            case Loadable.Loading<T>() -> loadingPanel(title);
            case Loadable.Failed<T>(Failure failure) ->
                panel(title, text(Placeholders.error(failure)).addClass(Theme.Classes.ERROR));
        };
    }

    /**
     * The pane that gets the focus back when Escape is pressed in the area with id {@code areaId}, if that
     * is a focusable area: the pane whose highlighted row it shows.
     */
    public Option<String> paneOfArea(Model model, String areaId) {
        return switch (areaId) {
            case "diff-staged", "diff-unstaged" -> Option.of(FilesPanel.ID);
            case COMMIT_AREA_ID -> Option.of(CommitsPanel.ID);
            case STASH_AREA_ID -> Option.of(StashPanel.ID);
            case DIVERGENCE_AREA_ID -> Option.of(BranchesPanel.ID);
            // It shows the pull request highlighted in whichever list of the pull requests screen is in use.
            case PULL_REQUEST_AREA_ID ->
                Option.of(model.prOverview().listPane().getOrElse(PullRequestsPanel.REVIEW_REQUESTED_ID));
            default -> Option.none();
        };
    }

    /**
     * The id of the area that shows what the pane {@code paneId} has highlighted, to move the focus into:
     * the staged diff if it shows anything, else the unstaged one, or the commit; none while it shows nothing.
     */
    public Option<String> areaOfPane(Model model, String paneId) {
        return focusableAreas
                .map(ScrollArea::id)
                .filter(id -> paneOfArea(model, id).contains(paneId) && showsContent(model, id))
                .headOption();
    }

    /**
     * Whether the area {@code areaId} has something to show in {@code model}.
     */
    private static boolean showsContent(Model model, String areaId) {
        return switch (areaId) {
            case "diff-staged" ->
                diffs(model).exists(diffs -> !shown(diffs.staged()).isBlank());
            case "diff-unstaged" ->
                diffs(model).exists(diffs -> !shown(diffs.unstaged()).isBlank());
            case COMMIT_AREA_ID ->
                model.repoView()
                        .commitDetail()
                        .map(Preview::shown)
                        .exists(detail -> detail.value().toOption().isDefined()
                                && commit(model, detail.key()).isDefined());
            case STASH_AREA_ID ->
                model.repoView()
                        .stashDetail()
                        .map(Preview::shown)
                        .exists(detail -> detail.value().toOption().isDefined()
                                && stash(model, detail.key()).isDefined());
            case DIVERGENCE_AREA_ID ->
                model.repoView()
                        .divergence()
                        .filter(view -> model.repoView().branchesTab() == BranchesTab.LOCAL)
                        .flatMap(view -> view.detail()
                                .map(Preview::shown)
                                .filter(detail -> detail.value().toOption().isDefined())
                                .flatMap(detail -> view.commit(detail.key())))
                        .isDefined();
            case PULL_REQUEST_AREA_ID ->
                model.prOverview()
                        .preview()
                        .map(Preview::shown)
                        .exists(preview -> preview.value().toOption().isDefined());
            default -> false;
        };
    }

    private static Option<Diffs> diffs(Model model) {
        return model.repoView()
                .fileDiff()
                .map(Preview::shown)
                .flatMap(diff -> diff.value().toOption());
    }

    /**
     * What goes with the pane that has the focus, {@code focusedId}; while a popup has it, what goes with the
     * pane the popup was opened from. That holds also in the frame a popup closes in, as it still has the focus
     * until the focus is given back.
     */
    public StyledElement<?> render(Model model, String focusedId) {
        boolean inContent = focusableAreas.exists(area -> area.id().equals(focusedId));
        focusableAreas.forEach(area -> area.tabbable(inContent));
        if (model.screen() == Screen.PULL_REQUESTS) {
            return pullRequestView(model, focusedId);
        }
        boolean onPane = inContent || LEFT_PANES.contains(focusedId);
        String pane = onPane ? focusedId : model.repoView().focusedPane().getOrElse(focusedId);
        if (BranchesPanel.ID.equals(pane) || DIVERGENCE_AREA_ID.equals(pane)) {
            return model.repoView()
                    .divergence()
                    .filter(view -> model.repoView().branchesTab() == BranchesTab.LOCAL)
                    .<StyledElement<?>>map(view -> divergenceCommitView(model, focusedId, view))
                    .getOrElse(() -> branchLogView(model, focusedId));
        }
        if (CommitsPanel.ID.equals(pane) || COMMIT_AREA_ID.equals(pane)) {
            return commitDiffView(model, focusedId);
        }
        if (StashPanel.ID.equals(pane) || STASH_AREA_ID.equals(pane)) {
            return stashDiffView(model, focusedId);
        }
        return fileDiffView(model, focusedId);
    }

    /**
     * The pull request highlighted in the pane of the pull requests screen in use, as {@link #pullRequestText}
     * shows it; the one shown before stays while it is read.
     */
    private StyledElement<?> pullRequestView(Model model, String focusedId) {
        String title = Messages.get("panel.pullRequest.title");
        return model.prOverview()
                .preview()
                .map(Preview::shown)
                .<StyledElement<?>>map(preview -> whenLoaded(
                        title,
                        preview.value(),
                        read -> pullRequestArea.show(
                                model,
                                focusedId,
                                Messages.get(
                                        "panel.pullRequest.titleFor",
                                        preview.key().repository(),
                                        String.valueOf(preview.key().number())),
                                new ScrolledContent.PullRequest(preview.key(), read),
                                pullRequestText(preview.key(), read, styles))))
                .getOrElse(() -> dimPanel(title, Messages.get("content.noPullRequest")));
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
     * One panel for the staged changes and one for the unstaged changes, one above the other when both exist.
     */
    private StyledElement<?> fileDiffView(Model model, String focusedId) {
        DiffOptions options = model.preferences().diffOptions();
        String title = titled(Messages.get("panel.diff.title"), options);
        // With no files there is nothing to highlight, so no diff is ever requested.
        return model.repo().status().toOption().exists(status -> status.files().isEmpty())
                ? dimPanel(title, Messages.get("content.noChangedFiles"))
                : model.repoView()
                        .fileDiff()
                        .map(Preview::shown)
                        .<StyledElement<?>>map(diff ->
                                whenLoaded(title, diff.value(), diffs -> diffPanels(model, focusedId, title, diffs)))
                        .getOrElse(() -> loadingPanel(title));
    }

    private StyledElement<?> diffPanels(Model model, String focusedId, String title, Diffs diffs) {
        DiffOptions options = model.preferences().diffOptions();
        if (diffs.isEmpty()) {
            return dimPanel(
                    title, Messages.get(options.ignoreWhitespace() ? "content.onlyWhitespace" : "content.noChanges"));
        }
        List<Element> panels = List.of(
                        Tuple.of(stagedArea, "content.staged", diffs.staged()),
                        Tuple.of(unstagedArea, "content.unstaged", diffs.unstaged()))
                .filter(panel -> !shown(panel._3).isBlank())
                .map(panel -> panel._1.show(
                        model,
                        focusedId,
                        titled(Messages.get(panel._2), options),
                        new ScrolledContent.Diff(panel._3),
                        diffText(shown(panel._3), styles)));
        return column(panels.toJavaArray(Element[]::new));
    }

    private StyledElement<?> branchLogView(Model model, String focusedId) {
        return model.repoView()
                .branchLog()
                .map(Preview::shown)
                .<StyledElement<?>>map(log -> {
                    String title = Messages.get("panel.log.titleFor", log.key().name());
                    AheadView ahead = new AheadView(
                            AheadUpdate.branch(model, log.key()),
                            model.repoView().onlyAhead());
                    return whenLoaded(ahead.title(title), log.value(), commits -> {
                        List<Commit> shown = ahead.shown(commits);
                        return logArea.show(
                                model,
                                focusedId,
                                ahead.title(title),
                                new ScrolledContent.BranchLog(shown),
                                logText(shown, ZoneId.systemDefault(), ahead, styles));
                    });
                })
                .getOrElse(() -> loadingPanel(Messages.get("panel.log.title")));
    }

    /**
     * The commit highlighted in the divergence {@code view}, as the commits pane shows its commits.
     */
    private StyledElement<?> divergenceCommitView(Model model, String focusedId, DivergenceView view) {
        DiffOptions options = model.preferences().diffOptions();
        String title = Messages.get("panel.commit.title");
        return view.detail()
                .map(Preview::shown)
                .<StyledElement<?>>map(detail -> whenLoaded(
                        title,
                        detail.value(),
                        changes -> view.commit(detail.key())
                                .<StyledElement<?>>map(commit -> divergenceArea.show(
                                        model,
                                        focusedId,
                                        titled(Messages.get("panel.commit.titleFor", commit.shortSha()), options),
                                        new ScrolledContent.CommitChanges(commit, changes),
                                        commitText(commit, shownChanges(changes, options), styles)))
                                .getOrElse(() -> loadingPanel(title))))
                .getOrElse(() -> view.commits()
                                .toOption()
                                .exists(divergence -> divergence.ahead().isEmpty()
                                        && divergence.behind().isEmpty())
                        ? dimPanel(title, Messages.get("content.noDivergence"))
                        : loadingPanel(title));
    }

    private StyledElement<?> commitDiffView(Model model, String focusedId) {
        DiffOptions options = model.preferences().diffOptions();
        String title = Messages.get("panel.commit.title");
        return model.repoView()
                .commitDetail()
                .map(Preview::shown)
                .<StyledElement<?>>map(detail -> whenLoaded(
                        title,
                        detail.value(),
                        changes -> commit(model, detail.key())
                                .<StyledElement<?>>map(commit -> commitArea.show(
                                        model,
                                        focusedId,
                                        titled(Messages.get("panel.commit.titleFor", commit.shortSha()), options),
                                        new ScrolledContent.CommitChanges(commit, changes),
                                        commitText(commit, shownChanges(changes, options), styles)))
                                .getOrElse(() -> loadingPanel(title))))
                .getOrElse(() -> model.repo().commits().toOption().exists(List::isEmpty)
                        ? panel(title, text(Messages.get("content.noCommits")))
                        : loadingPanel(title));
    }

    /**
     * What the highlighted stash stashed, as {@link #stashText} shows it; the stash shown before stays while it is
     * read. Without stashes, it says there are none.
     */
    private StyledElement<?> stashDiffView(Model model, String focusedId) {
        DiffOptions options = model.preferences().diffOptions();
        String title = Messages.get("panel.stash.title");
        return model.repoView()
                .stashDetail()
                .map(Preview::shown)
                .flatMap(detail -> stash(model, detail.key())
                        .<StyledElement<?>>map(stash -> whenLoaded(
                                title,
                                detail.value(),
                                (DiffText changes) -> stashArea.show(
                                        model,
                                        focusedId,
                                        titled(Messages.get("panel.stash.titleFor", stash.ref()), options),
                                        new ScrolledContent.StashChanges(stash.sha(), changes),
                                        stashText(stash, shownChanges(changes, options), styles)))))
                .getOrElse(() -> whenLoaded(
                        title,
                        model.repo().stashes(),
                        stashes -> stashes.isEmpty()
                                ? dimPanel(title, Messages.get("content.noStashes"))
                                : loadingPanel(title)));
    }

    /**
     * A scrollable area of the panel. It shows what it is given at the position the model has for that, and tells
     * where the user scrolled it to; the element only keeps the layout of the text.
     */
    private static final class ScrollArea {

        private static final int WHEEL_LINES = 3;

        private final String id;
        private final RichTextAreaElement element;
        private final BooleanSupplier leaveScrollArea;
        private final Consumer<String> focus;
        private final Consumer<Msg> dispatch;

        ScrollArea(String id, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
            this.id = id;
            this.leaveScrollArea = leaveScrollArea;
            this.focus = focus;
            this.dispatch = dispatch;
            element = richTextArea().id(id).rounded().scrollbar().fill();
        }

        String id() {
            return id;
        }

        /**
         * Makes the area part of the Tab cycle while {@code inContent}, so Tab goes from one area to the
         * other then, but from the last pane back to the first otherwise. Only the areas with a pane are.
         */
        void tabbable(boolean inContent) {
            element.focusable(isArea(id) && inContent);
        }

        /**
         * The area titled {@code title}, showing {@code text}, the text of {@code content}, where the model has it
         * scrolled to.
         */
        RichTextAreaElement show(Model model, String focusedId, String title, ScrolledContent content, Text text) {
            Scrolled at = model.scrolled(id, content);
            RichTextState state = element.text(text).title(title).state();
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
            if (event.kind() == MouseEventKind.PRESS && event.isLeftButton() && isArea(id)) {
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
    }
}
