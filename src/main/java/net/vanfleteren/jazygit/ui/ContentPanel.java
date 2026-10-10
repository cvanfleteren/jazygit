package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.i18n.Messages;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.CommitDetail;
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The right-hand content panel: it always mirrors whichever left pane currently has focus,
 * showing the file diff, the branch log, or the commit diff/body.
 */
public final class ContentPanel {

    /**
     * The scrollable areas for the diffs, the branch log and the commit. They are kept between renders because an area holds its own scroll
     * position; a new element every frame would jump back to the top. Only used from the render thread.
     */
    private static final ScrollArea STAGED_AREA =
            new ScrollArea("diff-staged", FilesPanel.ID, ContentPanel::diffText);
    private static final ScrollArea UNSTAGED_AREA =
            new ScrollArea("diff-unstaged", FilesPanel.ID, ContentPanel::diffText);
    private static final ScrollArea COMMIT_AREA =
            new ScrollArea("commit-changes", CommitsPanel.ID, ContentPanel::plainText);
    // Not focusable: clicking the log must not take the focus from the branches pane it mirrors.
    private static final ScrollArea LOG_AREA =
            new ScrollArea("branch-log", Optional.empty(), ContentPanel::logText);
    // Called when Escape is pressed; gives the focus back to the file tree if a focusable area had it, and
    // says whether it did.
    private static Supplier<Boolean> leaveScrollArea = () -> false;

    public static void onLeaveScrollArea(Supplier<Boolean> leave) {
        leaveScrollArea = leave;
    }

    /**
     * The pane that gets the focus back when Escape is pressed in the area with id {@code areaId}, if that
     * is a focusable area.
     */
    public static Optional<String> paneOfArea(String areaId) {
        return Stream.of(STAGED_AREA, UNSTAGED_AREA, COMMIT_AREA)
                .filter(area -> area.id().equals(areaId))
                .flatMap(area -> area.pane.stream())
                .findFirst();
    }

    /**
     * The id of the area that shows what the pane {@code paneId} has highlighted, to move the focus into:
     * the staged diff if it shows anything, else the unstaged one, or the commit.
     */
    public static Optional<String> areaOfPane(String paneId) {
        return Stream.of(STAGED_AREA, UNSTAGED_AREA, COMMIT_AREA)
                .filter(area -> area.pane.filter(paneId::equals).isPresent() && area.hasContent())
                .map(ScrollArea::id)
                .findFirst();
    }

    private static final String MESSAGE_INDENT = "    ";

    private ContentPanel() {
    }

    private static final class ScrollArea {
        private final String id;
        // The pane this area shows the content of, which gets the focus back from it; none if the area
        // cannot be focused.
        private final Optional<String> pane;
        private final RichTextAreaElement element;
        private final Function<String, Text> styling;
        private Object key = "";

        ScrollArea(String id, String pane, Function<String, Text> styling) {
            this(id, Optional.of(pane), styling);
        }

        ScrollArea(String id, Optional<String> pane, Function<String, Text> styling) {
            this.id = id;
            this.pane = pane;
            this.styling = styling;
            element = richTextArea().id(id).rounded().scrollbar().fill();
            if (pane.isPresent()) {
                element.focusable();
            }
            element.onKeyEvent(event -> {
                // Key handlers also see keys typed elsewhere; the callback checks that a diff has focus.
                return event.code() == KeyCode.ESCAPE && leaveScrollArea.get() ? EventResult.HANDLED : EventResult.UNHANDLED;
            });
        }

        String id() {
            return id;
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
                element.text(text.get());
                element.state().scrollToTop();
            }
            return this;
        }

        boolean hasContent() {
            return !(key instanceof String content) || !content.isBlank();
        }

        RichTextAreaElement show(String title) {
            return element.title(title);
        }
    }

    private static DateTimeFormatter dateTime() {
        return DateTimeFormatter.ofPattern(Messages.get("content.dateTimePattern"));
    }

    public static StyledElement<?> render(Model model, String focusedId, int commitsSelection) {
        if (BranchesPanel.ID.equals(focusedId)) {
            return branchLogView(model);
        }
        if (CommitsPanel.ID.equals(focusedId) || COMMIT_AREA.id().equals(focusedId)) {
            return commitDiffView(model);
        }
        return fileDiffView(model);
    }

    /**
     * One panel for the staged changes and one for the unstaged changes, one above the other when both exist.
     */
    private static StyledElement<?> fileDiffView(Model model) {
        // With no files there is nothing to highlight, so no diff is ever requested.
        if (Update.files(model).isEmpty() && model.status() instanceof Loadable.Loaded<RepoStatus>) {
            return panel(Messages.get("panel.diff.title"), text(Messages.get("content.noChangedFiles")).dim()).rounded();
        }
        return model.fileDiff()
                .map(ContentPanel::shown)
                .map(d -> switch (d.diff()) {
                    case Loadable.Loaded<Diffs>(Diffs diffs) -> diffPanels(diffs);
                    case Loadable.Loading<Diffs>() -> panel(Messages.get("panel.diff.title"), text(Placeholders.loading()).dim()).rounded();
                    case Loadable.Failed<Diffs>(String message) ->
                            panel(Messages.get("panel.diff.title"), text(Placeholders.error(message))).rounded();
                })
                .orElseGet(() -> panel(Messages.get("panel.diff.title"), text(Placeholders.loading()).dim()).rounded());
    }

    private static StyledElement<?> diffPanels(Diffs diffs) {
        if (diffs.isEmpty()) {
            return panel(Messages.get("panel.diff.title"), text(Messages.get("content.noChanges")).dim()).rounded();
        }
        List<Element> panels = Stream.of(
                        Map.entry(Messages.get("content.staged"), STAGED_AREA.with(diffs.staged())),
                        Map.entry(Messages.get("content.unstaged"), UNSTAGED_AREA.with(diffs.unstaged())))
                .filter(e -> e.getValue().hasContent())
                .map(e -> (Element) e.getValue().show(e.getKey()))
                .toList();
        return column(panels.toArray(Element[]::new));
    }

    /**
     * While a diff is loading, keep showing the previously loaded one rather than flashing a placeholder.
     */
    private static FileDiff shown(FileDiff diff) {
        return diff.diff() instanceof Loadable.Loading<Diffs> ? diff.previous().orElse(diff) : diff;
    }

    /**
     * The unified diff as one styled text, so it is a single element to lay out however long the diff is.
     */
    static Text diffText(String diff) {
        return Text.from(diff.lines().map(ContentPanel::diffLine).toList());
    }

    /**
     * The branch log: the {@code commit <sha>} part of each commit's first line is yellow.
     */
    static Text logText(String content) {
        return Text.from(content.lines().map(line -> line.startsWith("* ")
                        ? Line.from(Span.raw("* "), Span.styled(line.substring(2), Style.EMPTY.fg(Color.YELLOW)))
                        : Line.from(Span.raw(line)))
                .toList());
    }

    static Text plainText(String content) {
        return Text.from(content.lines().map(line -> Line.from(Span.raw(line))).toList());
    }

    /**
     * A line of a unified diff, colored by what it is: added, removed, a hunk header or a file header.
     */
    static Line diffLine(String line) {
        if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ")) {
            return Line.from(Span.styled(line, Style.EMPTY.bold()));
        }
        if (line.startsWith("+")) {
            return Line.from(Span.styled(line, Style.EMPTY.fg(Color.GREEN)));
        }
        if (line.startsWith("-")) {
            return Line.from(Span.styled(line, Style.EMPTY.fg(Color.RED)));
        }
        if (line.startsWith("@@")) {
            return Line.from(Span.styled(line, Style.EMPTY.fg(Color.CYAN)));
        }
        return Line.from(Span.raw(line));
    }

    private static StyledElement<?> branchLogView(Model model) {
        return model.branchLog()
                .map(ContentPanel::shown)
                .<StyledElement<?>>map(log -> whenLoaded(Messages.get("panel.log.titleFor", log.branch()), log.commits(), commits ->
                        LOG_AREA.with(String.join("\n", logLines(commits, ZoneId.systemDefault()))).show(Messages.get("panel.log.titleFor", log.branch()))))
                .orElseGet(() -> panel(Messages.get("panel.log.title"), text(Placeholders.loading()).dim()).rounded());
    }

    /**
     * While a log is loading, keep showing the previously loaded one rather than flashing a placeholder.
     */
    private static BranchLog shown(BranchLog log) {
        return log.commits() instanceof Loadable.Loading<List<Commit>> ? log.previous().orElse(log) : log;
    }

    /**
     * The commits as a {@code git log}-style listing, with dates in {@code zone}. Like a graph, the first
     * line of each commit starts with {@code "* "} and its other lines with {@code "| "}. The message is
     * indented.
     */
    static List<String> logLines(List<Commit> commits, ZoneId zone) {
        return commits.stream()
                .flatMap(c -> {
                    List<String> lines = Stream.concat(
                            Stream.of(Messages.get("content.commitSha", c.shortSha()), author(c), dateTime().format(c.authorTime().atZone(zone)),
                                    "", MESSAGE_INDENT + c.message(), ""),
                            c.body().isBlank() ? Stream.empty() : Stream.concat(
                                    c.body().lines().map(line -> line.isBlank() ? line : MESSAGE_INDENT + line), Stream.of(""))).toList();
                    return IntStream.range(0, lines.size()).mapToObj(i -> (i == 0 ? "* " : "| ") + lines.get(i));
                })
                .toList();
    }

    private static String author(Commit commit) {
        return Messages.get("content.author", commit.authorName(), commit.authorEmail());
    }

    /**
     * What the commit area shows: the commit, and what it changed.
     */
    private record CommitContent(Commit commit, String changes) {
    }

    private static StyledElement<?> commitDiffView(Model model) {
        String title = Messages.get("panel.commit.title");
        return model.commitDetail()
                .map(ContentPanel::shown)
                .<StyledElement<?>>map(detail -> switch (detail.changes()) {
                    case Loadable.Loaded<String>(String changes) -> commit(model, detail.sha())
                            .<StyledElement<?>>map(commit -> COMMIT_AREA
                                    .with(new CommitContent(commit, changes), () -> commitText(commit, changes))
                                    .show(Messages.get("panel.commit.titleFor", commit.shortSha())))
                            .orElseGet(() -> panel(title, text(Placeholders.loading()).dim()).rounded());
                    case Loadable.Loading<String>() -> panel(title, text(Placeholders.loading()).dim()).rounded();
                    case Loadable.Failed<String>(String message) ->
                            panel(title, text(Placeholders.error(message))).rounded();
                })
                .orElseGet(() -> model.commits() instanceof Loadable.Loaded<List<Commit>>(List<Commit> commits)
                        && commits.isEmpty()
                        ? panel(title, text(Messages.get("content.noCommits"))).rounded()
                        : panel(title, text(Placeholders.loading()).dim()).rounded());
    }

    private static Optional<Commit> commit(Model model, String sha) {
        return model.commits() instanceof Loadable.Loaded<List<Commit>>(List<Commit> commits)
                ? commits.stream().filter(c -> c.sha().equals(sha)).findFirst()
                : Optional.empty();
    }

    /**
     * While the changes of a commit are loading, keep showing the previously loaded ones rather than
     * flashing a placeholder.
     */
    private static CommitDetail shown(CommitDetail detail) {
        return detail.changes() instanceof Loadable.Loading<String> ? detail.previous().orElse(detail) : detail;
    }

    /**
     * Like {@code git show}: the commit's header, its indented message, a {@code ---} line and then the changes.
     */
    static Text commitText(Commit commit, String changes) {
        Stream<Line> header = Stream.of(
                Line.from(Span.styled(Messages.get("content.commitSha", commit.sha()), Style.EMPTY.fg(Color.YELLOW))),
                Line.from(Span.raw(Messages.get("content.commitAuthor", commit.authorName(), commit.authorEmail()))),
                Line.from(Span.raw(Messages.get("content.commitDate", commitDate().format(
                        commit.authorTime().atZone(ZoneId.systemDefault()))))),
                Line.from(Span.raw("")),
                Line.from(Span.styled(MESSAGE_INDENT + commit.message(), Style.EMPTY.bold())));
        Stream<Line> body = commit.body().isBlank()
                ? Stream.empty()
                : Stream.concat(Stream.of(Line.from(Span.raw(""))), commit.body().lines().map(l -> Line.from(Span.raw(l.isBlank() ? l : MESSAGE_INDENT + l))));
        return Text.from(Stream.of(header, body, Stream.of(Line.from(Span.raw("---"))), changes.lines().map(ContentPanel::diffLine))
                .flatMap(lines -> lines)
                .toList());
    }

    private static DateTimeFormatter commitDate() {
        return DateTimeFormatter.ofPattern(Messages.get("content.commitDatePattern"), Locale.ENGLISH);
    }

    private static <T> StyledElement<?> whenLoaded(String title, Loadable<T> loadable, Function<T, StyledElement<?>> view) {
        return switch (loadable) {
            case Loadable.Loaded<T>(T value) -> view.apply(value);
            case Loadable.Loading<T>() -> panel(title, text(Placeholders.loading()).dim()).rounded();
            case Loadable.Failed<T>(String message) -> panel(title, text(Placeholders.error(message))).rounded();
        };
    }
}
