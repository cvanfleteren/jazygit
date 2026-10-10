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
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The right-hand content panel: it always mirrors whichever left pane currently has focus,
 * showing the file diff, the branch log, or the commit diff/body.
 */
public final class ContentPanel {

    /**
     * The scrollable areas for the diffs and the branch log. They are kept between renders because an area holds its own scroll
     * position; a new element every frame would jump back to the top. Only used from the render thread.
     */
    private static final ScrollArea STAGED_AREA = new ScrollArea("diff-staged", true, ContentPanel::diffText);
    private static final ScrollArea UNSTAGED_AREA = new ScrollArea("diff-unstaged", true, ContentPanel::diffText);
    // Not focusable: clicking the log must not take the focus from the branches pane it mirrors.
    private static final ScrollArea LOG_AREA = new ScrollArea("branch-log", false, ContentPanel::plainText);
    // Called when Escape is pressed; gives the focus back to the file tree if a focusable area had it, and
    // says whether it did.
    private static Supplier<Boolean> leaveScrollArea = () -> false;

    public static void onLeaveScrollArea(Supplier<Boolean> leave) {
        leaveScrollArea = leave;
    }

    public static boolean isFocusableAreaId(String id) {
        return STAGED_AREA.id().equals(id) || UNSTAGED_AREA.id().equals(id);
    }

    /**
     * The id of the focusable area to focus: the staged one if it shows anything, else the unstaged one.
     */
    public static Optional<String> firstFocusableAreaId() {
        return Stream.of(STAGED_AREA, UNSTAGED_AREA).filter(ScrollArea::hasContent).map(ScrollArea::id).findFirst();
    }

    private ContentPanel() {
    }

    private static final class ScrollArea {
        private final String id;
        private final RichTextAreaElement element;
        private final Function<String, Text> styling;
        private String content = "";

        ScrollArea(String id, boolean focusable, Function<String, Text> styling) {
            this.id = id;
            this.styling = styling;
            element = richTextArea().id(id).rounded().scrollbar().fill();
            if (focusable) {
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
            if (!newContent.equals(content)) {
                content = newContent;
                element.text(styling.apply(newContent));
                element.state().scrollToTop();
            }
            return this;
        }

        boolean hasContent() {
            return !content.isBlank();
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
        if (CommitsPanel.ID.equals(focusedId)) {
            return commitDiffView(model, commitsSelection);
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
     * The commits as a {@code git log}-style listing, with dates in {@code zone}.
     */
    static List<String> logLines(List<Commit> commits, ZoneId zone) {
        return commits.stream()
                .flatMap(c -> Stream.concat(
                        Stream.of(Messages.get("content.commitSha", c.shortSha()), author(c), dateTime().format(c.authorTime().atZone(zone)),
                                "", c.message(), ""),
                        c.body().isBlank() ? Stream.empty() : Stream.concat(c.body().lines(), Stream.of(""))))
                .toList();
    }

    private static String author(Commit commit) {
        return Messages.get("content.author", commit.authorName(), commit.authorEmail());
    }

    private static StyledElement<?> commitDiffView(Model model, int commitsSelection) {
        return whenLoaded(Messages.get("panel.commit.title"), model.commits(), commits -> {
            if (commits.isEmpty()) {
                return panel(Messages.get("panel.commit.title"), text(Messages.get("content.noCommits"))).rounded();
            }
            Commit commit = commits.get(clamp(commitsSelection, commits.size()));
            return panel(Messages.get("panel.commit.titleFor", commit.shortSha()),
                    text(commit.message()).bold(),
                    text(Messages.get("content.authoredOn", author(commit),
                    dateTime().format(commit.authorTime().atZone(ZoneId.systemDefault()))))
                            .dim(),
                    spacer(),
                    text(commit.body()))
                    .rounded();
        });
    }


    private static <T> StyledElement<?> whenLoaded(String title, Loadable<T> loadable, Function<T, StyledElement<?>> view) {
        return switch (loadable) {
            case Loadable.Loaded<T>(T value) -> view.apply(value);
            case Loadable.Loading<T>() -> panel(title, text(Placeholders.loading()).dim()).rounded();
            case Loadable.Failed<T>(String message) -> panel(title, text(Placeholders.error(message))).rounded();
        };
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size - 1));
    }
}
