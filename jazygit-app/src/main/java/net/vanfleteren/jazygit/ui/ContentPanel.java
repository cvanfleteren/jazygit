package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.DivergenceView;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.Screen;

/**
 * The right-hand content panel: it always mirrors whichever left pane currently has focus,
 * showing the file diff, the branch log, or the commit diff/body; on the pull requests screen, the highlighted
 * pull request.
 */
public final class ContentPanel {

    private static final String COMMIT_AREA_ID = "commit-changes";
    private static final String DIVERGENCE_AREA_ID = "divergence-commit";
    private static final String PULL_REQUEST_AREA_ID = "pull-request-preview";
    private static final List<String> LEFT_PANES =
            List.of(FilesPanel.ID, BranchesPanel.ID, CommitsPanel.ID, StashPanel.ID);
    private static final String MESSAGE_INDENT = "    ";
    private final Theme.SpanStyles styles;
    // The scrollable areas for the diffs, the branch log and the commit. They are kept between renders because
    // an area holds its own scroll position; a new element every frame would jump back to the top. Only used
    // from the render thread.
    private final ScrollArea stagedArea;
    private final ScrollArea unstagedArea;
    private final ScrollArea commitArea;
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
        this.styles = styles;
        stagedArea = new ScrollArea(
                "diff-staged", Option.of(FilesPanel.ID), text -> diffText(text, styles), leaveScrollArea, focus);
        unstagedArea = new ScrollArea(
                "diff-unstaged", Option.of(FilesPanel.ID), text -> diffText(text, styles), leaveScrollArea, focus);
        commitArea = new ScrollArea(
                COMMIT_AREA_ID, Option.of(CommitsPanel.ID), ContentPanel::plainText, leaveScrollArea, focus);
        divergenceArea = new ScrollArea(
                DIVERGENCE_AREA_ID, Option.of(BranchesPanel.ID), ContentPanel::plainText, leaveScrollArea, focus);
        logArea = new ScrollArea("branch-log", Option.none(), text -> logText(text, styles), leaveScrollArea, focus);
        // It shows the pull request highlighted in whichever pane of the pull requests screen is in use.
        pullRequestArea = new ScrollArea(
                PULL_REQUEST_AREA_ID,
                Option.of(PullRequestsPanel.REVIEW_REQUESTED_ID),
                ContentPanel::plainText,
                leaveScrollArea,
                focus);
        focusableAreas = List.of(stagedArea, unstagedArea, commitArea, divergenceArea, pullRequestArea);
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
     * The branch log: the {@code commit <sha>} part of each commit's first line is yellow.
     */
    static Text logText(String content, Theme.SpanStyles styles) {
        return styledText(lines(content)
                .map(line -> line.startsWith("* ")
                        ? Line.from(Span.raw("* "), styled(line.substring(2), Theme.Classes.CURRENT_BRANCH, styles))
                        : Line.from(Span.raw(line))));
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
        return line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ")
                ? Option.of(Theme.Classes.DIFF_HEADER)
                : line.startsWith("+")
                        ? Option.of(Theme.Classes.DIFF_ADDED)
                        : line.startsWith("-")
                                ? Option.of(Theme.Classes.DIFF_REMOVED)
                                : line.startsWith("@@") ? Option.of(Theme.Classes.DIFF_HUNK) : Option.none();
    }

    /**
     * The commits as a {@code git log}-style listing, with dates in {@code zone}. Like a graph, the first
     * line of each commit starts with {@code "* "} and its other lines with {@code "| "}. The message is
     * indented.
     */
    static List<String> logLines(List<Commit> commits, ZoneId zone) {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(Messages.get("content.dateTimePattern"));
        return CommitGraph.layout(commits).flatMap(node -> {
            Commit c = node.commit();
            String rails = "| ".repeat(node.depth());
            List<String> lines = List.of(
                            Messages.get("content.commitSha", c.shortSha()),
                            Messages.get("content.author", c.authorName(), c.authorEmail()),
                            dateTime.format(c.authorTime().atZone(zone)),
                            "")
                    .appendAll(messageLines(c))
                    .append("");
            // Like git's graph: a merge opens a rail with a "\\" line, the last commit of a merged
            // branch closes it with a "/" in place of its second line.
            String closing = "| ".repeat(Math.max(0, node.depth() - 1));
            return c.isMerge()
                    ? List.of(
                                    rails + "*   " + lines.head(),
                                    rails + "|\\  "
                                            + Messages.get(
                                                    "content.merge",
                                                    c.parents()
                                                            .map(Sha::shortSha)
                                                            .mkString(" ")))
                            .appendAll(lines.tail().map(line -> rails + "| | " + line))
                    : lines.zipWithIndex((line, i) -> i == 0
                            ? rails + "* " + line
                            : node.closesBranch() ? closing + (i == 1 ? "|/  " : "|   ") + line : rails + "| " + line);
        });
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
        return model.commits().getOrElse(List.empty()).find(c -> c.sha().equals(sha));
    }

    /**
     * Like {@code git show}: the commit's header, its indented message, a {@code ---} line and then the changes.
     */
    static Text commitText(Commit commit, String changes, Theme.SpanStyles styles) {
        DateTimeFormatter commitDate =
                DateTimeFormatter.ofPattern(Messages.get("content.commitDatePattern"), Locale.ENGLISH);
        List<String> message = messageLines(commit);
        return styledText(List.of(
                        styled(Messages.get("content.commitSha", commit.sha().value()), Theme.Classes.SHA, styles),
                        Span.raw(Messages.get("content.commitAuthor", commit.authorName(), commit.authorEmail())),
                        Span.raw(Messages.get(
                                "content.commitDate",
                                commitDate.format(commit.authorTime().atZone(ZoneId.systemDefault())))),
                        Span.raw(""),
                        styled(message.head(), Theme.Classes.BOLD, styles))
                .map(Line::from)
                .appendAll(message.tail().map(line -> Line.from(Span.raw(line))))
                .append(Line.from(Span.raw("---")))
                .appendAll(lines(changes).map(line -> diffLine(line, styles))));
    }

    private static List<String> lines(String text) {
        return List.ofAll(text.lines()::iterator);
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
     * is a focusable area.
     */
    public Option<String> paneOfArea(String areaId) {
        return focusableAreas
                .filter(area -> area.id().equals(areaId))
                .flatMap(area -> area.pane)
                .headOption();
    }

    /**
     * The id of the area that shows what the pane {@code paneId} has highlighted, to move the focus into:
     * the staged diff if it shows anything, else the unstaged one, or the commit.
     */
    public Option<String> areaOfPane(String paneId) {
        return focusableAreas
                .filter(area -> area.pane.contains(paneId) && area.hasContent())
                .map(ScrollArea::id)
                .headOption();
    }

    /**
     * What goes with the pane that has the focus, {@code focusedId}; while a popup has it, what goes with the
     * pane the popup was opened from. That holds also in the frame a popup closes in, as it still has the focus
     * until the focus is given back.
     */
    public StyledElement<?> render(Model model, String focusedId) {
        List.of(stagedArea, unstagedArea, commitArea, divergenceArea, logArea, pullRequestArea)
                .forEach(area -> area.restyle(model.theme()));
        boolean inContent = focusableAreas.exists(area -> area.id().equals(focusedId));
        focusableAreas.forEach(area -> area.tabbable(inContent));
        if (model.screen() == Screen.PULL_REQUESTS) {
            return pullRequestView(model, focusedId);
        }
        boolean onPane = inContent || LEFT_PANES.contains(focusedId);
        String pane = onPane ? focusedId : model.focusedPane().getOrElse(focusedId);
        if (BranchesPanel.ID.equals(pane) || DIVERGENCE_AREA_ID.equals(pane)) {
            return model.divergence()
                    .filter(view -> model.branchesTab() == BranchesTab.LOCAL)
                    .<StyledElement<?>>map(view -> divergenceCommitView(view, model.diffOptions()))
                    .getOrElse(() -> branchLogView(model));
        }
        if (CommitsPanel.ID.equals(pane) || COMMIT_AREA_ID.equals(pane)) {
            return commitDiffView(model);
        }
        return fileDiffView(model);
    }

    /**
     * The pull request highlighted in the pane of the pull requests screen in use, as {@link #pullRequestText}
     * shows it; the one shown before stays while it is read.
     */
    private StyledElement<?> pullRequestView(Model model, String focusedId) {
        Scopes.PULL_REQUESTS.map(Keymap.Scope::id).find(focusedId::equals).forEach(pullRequestArea::showsPane);
        String title = Messages.get("panel.pullRequest.title");
        return model.prOverview()
                .preview()
                .map(Preview::shown)
                .<StyledElement<?>>map(preview -> whenLoaded(
                        title,
                        preview.value(),
                        read -> pullRequestArea
                                .with(
                                        new PullRequestContent(preview.key(), read),
                                        () -> pullRequestText(preview.key(), read, styles))
                                .show(Messages.get(
                                        "panel.pullRequest.titleFor",
                                        preview.key().repository(),
                                        String.valueOf(preview.key().number())))))
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
    private StyledElement<?> fileDiffView(Model model) {
        DiffOptions options = model.diffOptions();
        String title = titled(Messages.get("panel.diff.title"), options);
        // With no files there is nothing to highlight, so no diff is ever requested.
        return model.status().toOption().exists(status -> status.files().isEmpty())
                ? dimPanel(title, Messages.get("content.noChangedFiles"))
                : model.fileDiff()
                        .map(Preview::shown)
                        .<StyledElement<?>>map(
                                diff -> whenLoaded(title, diff.value(), diffs -> diffPanels(title, diffs, options)))
                        .getOrElse(() -> loadingPanel(title));
    }

    private StyledElement<?> diffPanels(String title, Diffs diffs, DiffOptions options) {
        if (diffs.isEmpty()) {
            return dimPanel(
                    title, Messages.get(options.ignoreWhitespace() ? "content.onlyWhitespace" : "content.noChanges"));
        }
        List<Element> panels = List.of(
                        Tuple.of(
                                titled(Messages.get("content.staged"), options),
                                stagedArea.with(shown(diffs.staged()))),
                        Tuple.of(
                                titled(Messages.get("content.unstaged"), options),
                                unstagedArea.with(shown(diffs.unstaged()))))
                .filter(e -> e._2.hasContent())
                .map(e -> e._2.show(e._1));
        return column(panels.toJavaArray(Element[]::new));
    }

    private StyledElement<?> branchLogView(Model model) {
        return model.branchLog()
                .map(Preview::shown)
                .<StyledElement<?>>map(log -> {
                    String title = Messages.get("panel.log.titleFor", log.key().name());
                    return whenLoaded(
                            title,
                            log.value(),
                            commits -> logArea.with(logLines(commits, ZoneId.systemDefault())
                                            .mkString("\n"))
                                    .show(title));
                })
                .getOrElse(() -> loadingPanel(Messages.get("panel.log.title")));
    }

    /**
     * The commit highlighted in the divergence {@code view}, as the commits pane shows its commits.
     */
    private StyledElement<?> divergenceCommitView(DivergenceView view, DiffOptions options) {
        String title = Messages.get("panel.commit.title");
        return view.detail()
                .map(Preview::shown)
                .<StyledElement<?>>map(detail -> whenLoaded(
                        title,
                        detail.value(),
                        changes -> view.commit(detail.key())
                                .<StyledElement<?>>map(commit -> divergenceArea
                                        .with(
                                                new CommitContent(commit, changes),
                                                () -> commitText(commit, shownChanges(changes, options), styles))
                                        .show(titled(
                                                Messages.get("panel.commit.titleFor", commit.shortSha()), options)))
                                .getOrElse(() -> loadingPanel(title))))
                .getOrElse(() -> view.commits()
                                .toOption()
                                .exists(divergence -> divergence.ahead().isEmpty()
                                        && divergence.behind().isEmpty())
                        ? dimPanel(title, Messages.get("content.noDivergence"))
                        : loadingPanel(title));
    }

    private StyledElement<?> commitDiffView(Model model) {
        DiffOptions options = model.diffOptions();
        String title = Messages.get("panel.commit.title");
        return model.commitDetail()
                .map(Preview::shown)
                .<StyledElement<?>>map(detail -> whenLoaded(
                        title,
                        detail.value(),
                        changes -> commit(model, detail.key())
                                .<StyledElement<?>>map(commit -> commitArea
                                        .with(
                                                new CommitContent(commit, changes),
                                                () -> commitText(commit, shownChanges(changes, options), styles))
                                        .show(titled(
                                                Messages.get("panel.commit.titleFor", commit.shortSha()), options)))
                                .getOrElse(() -> loadingPanel(title))))
                .getOrElse(() -> model.commits().toOption().exists(List::isEmpty)
                        ? panel(title, text(Messages.get("content.noCommits")))
                        : loadingPanel(title));
    }

    private static final class ScrollArea {
        private final String id;
        // The pane this area shows the content of, which gets the focus back from it; none if the area
        // cannot be focused.
        private Option<String> pane;
        private final RichTextAreaElement element;
        private final Function<String, Text> styling;
        // What is shown, which only changes the text and the scroll position when it changes.
        private Object key = "";
        // How the text of what is shown is built, and the theme it was built in.
        private Supplier<Text> text = Text::empty;
        private Option<ThemeName> theme = Option.none();

        ScrollArea(
                String id,
                Option<String> pane,
                Function<String, Text> styling,
                BooleanSupplier leaveScrollArea,
                Consumer<String> focus) {
            this.id = id;
            this.pane = pane;
            this.styling = styling;
            element = richTextArea().id(id).rounded().scrollbar().fill();
            // Only focusable, and so part of the Tab cycle, while the content has the focus; a click still
            // gives it the focus from the panes.
            if (pane.isDefined()) {
                element.onMouseEvent(event -> {
                    if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
                        focus.accept(id);
                        return EventResult.HANDLED;
                    }
                    return EventResult.UNHANDLED;
                });
            }
            element.onKeyEvent(event -> {
                // Key handlers also see keys typed elsewhere; the callback checks that a diff has focus.
                return event.code() == KeyCode.ESCAPE && leaveScrollArea.getAsBoolean()
                        ? EventResult.HANDLED
                        : EventResult.UNHANDLED;
            });
        }

        String id() {
            return id;
        }

        /**
         * Makes the area show the content of the pane {@code paneId}, which gets the focus back from it.
         */
        void showsPane(String paneId) {
            pane = Option.of(paneId);
        }

        /**
         * Shows {@code newContent}, scrolling back to the top when it differs from what was shown.
         */
        ScrollArea with(String newContent) {
            return with(newContent, () -> styling.apply(newContent));
        }

        /**
         * Shows the text for {@code newKey}, which is only built, and the scroll position only reset to
         * the top, when the key differs from the one shown.
         */
        ScrollArea with(Object newKey, Supplier<Text> text) {
            if (!newKey.equals(key)) {
                key = newKey;
                this.text = text;
                element.text(text.get());
                element.state().scrollToTop();
            }
            return this;
        }

        /**
         * Builds the text shown again when the theme is not {@code current} anymore, as its colours are
         * resolved when it is built; the scroll position stays.
         */
        void restyle(ThemeName current) {
            if (!theme.contains(current)) {
                theme = Option.of(current);
                element.text(text.get());
            }
        }

        boolean hasContent() {
            return !(key instanceof String content) || !content.isBlank();
        }

        /**
         * Makes the area part of the Tab cycle while {@code inContent}, so Tab goes from one area to the
         * other then, but from the last pane back to the first otherwise.
         */
        void tabbable(boolean inContent) {
            element.focusable(pane.isDefined() && inContent);
        }

        RichTextAreaElement show(String title) {
            return element.title(title);
        }
    }

    /**
     * What the commit area shows: the commit, and what it changed.
     */
    private record CommitContent(Commit commit, DiffText changes) {}

    /**
     * What the pull request area shows: the pull request, and what it says and changes.
     */
    private record PullRequestContent(PullRequestSummary pullRequest, PullRequestPreview preview) {}
}
