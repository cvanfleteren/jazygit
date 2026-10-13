package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import static dev.tamboui.toolkit.Toolkit.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

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
            new ScrollArea("branch-log", Option.none(), ContentPanel::logText);
    private static final List<ScrollArea> FOCUSABLE_AREAS = List.of(STAGED_AREA, UNSTAGED_AREA, COMMIT_AREA);
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
    public static Option<String> paneOfArea(String areaId) {
        return FOCUSABLE_AREAS
                .filter(area -> area.id().equals(areaId))
                .flatMap(area -> area.pane)
                .headOption();
    }

    /**
     * The id of the area that shows what the pane {@code paneId} has highlighted, to move the focus into:
     * the staged diff if it shows anything, else the unstaged one, or the commit.
     */
    public static Option<String> areaOfPane(String paneId) {
        return FOCUSABLE_AREAS
                .filter(area -> area.pane.contains(paneId) && area.hasContent())
                .map(ScrollArea::id)
                .headOption();
    }

    private static final String MESSAGE_INDENT = "    ";

    private ContentPanel() {
    }

    private static final class ScrollArea {
        private final String id;
        // The pane this area shows the content of, which gets the focus back from it; none if the area
        // cannot be focused.
        private final Option<String> pane;
        private final RichTextAreaElement element;
        private final Function<String, Text> styling;
        private Object key = "";

        ScrollArea(String id, String pane, Function<String, Text> styling) {
            this(id, Option.of(pane), styling);
        }

        ScrollArea(String id, Option<String> pane, Function<String, Text> styling) {
            this.id = id;
            this.pane = pane;
            this.styling = styling;
            element = richTextArea().id(id).rounded().scrollbar().fill();
            if (pane.isDefined()) {
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

    public static StyledElement<?> render(Model model, String focusedId) {
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
        String title = Messages.get("panel.diff.title");
        // With no files there is nothing to highlight, so no diff is ever requested.
        return model.status().toOption().exists(status -> status.files().isEmpty())
                ? dimPanel(title, Messages.get("content.noChangedFiles"))
                : model.fileDiff()
                .map(Preview::shown)
                .<StyledElement<?>>map(diff -> whenLoaded(title, diff.value(), diffs -> diffPanels(title, diffs)))
                .getOrElse(() -> loadingPanel(title));
    }

    private static StyledElement<?> diffPanels(String title, Diffs diffs) {
        if (diffs.isEmpty()) {
            return dimPanel(title, Messages.get("content.noChanges"));
        }
        List<Element> panels = List.of(
                        Tuple.of(Messages.get("content.staged"), STAGED_AREA.with(diffs.staged())),
                        Tuple.of(Messages.get("content.unstaged"), UNSTAGED_AREA.with(diffs.unstaged())))
                .filter(e -> e._2.hasContent())
                .map(e -> e._2.show(e._1));
        return column(panels.toJavaArray(Element[]::new));
    }

    /**
     * The unified diff as one styled text, so it is a single element to lay out however long the diff is.
     */
    static Text diffText(String diff) {
        return styledText(lines(diff).map(ContentPanel::diffLine));
    }

    /**
     * The branch log: the {@code commit <sha>} part of each commit's first line is yellow.
     */
    static Text logText(String content) {
        return styledText(lines(content).map(line -> line.startsWith("* ")
                ? Line.from(Span.raw("* "), styled(line.substring(2), Theme.Classes.CURRENT_BRANCH))
                : Line.from(Span.raw(line))));
    }

    static Text plainText(String content) {
        return styledText(lines(content).map(line -> Line.from(Span.raw(line))));
    }

    /**
     * A line of a unified diff, colored by what it is: added, removed, a hunk header or a file header.
     */
    static Line diffLine(String line) {
        return diffClass(line).map(cssClass -> Line.from(styled(line, cssClass))).getOrElse(() -> Line.from(Span.raw(line)));
    }

    private static Option<String> diffClass(String line) {
        return line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ")
                ? Option.of(Theme.Classes.DIFF_HEADER)
                : line.startsWith("+") ? Option.of(Theme.Classes.DIFF_ADDED)
                : line.startsWith("-") ? Option.of(Theme.Classes.DIFF_REMOVED)
                : line.startsWith("@@") ? Option.of(Theme.Classes.DIFF_HUNK)
                : Option.none();
    }

    private static StyledElement<?> branchLogView(Model model) {
        return model.branchLog()
                .map(Preview::shown)
                .<StyledElement<?>>map(log -> {
                    String title = Messages.get("panel.log.titleFor", log.key());
                    return whenLoaded(title, log.value(), commits ->
                            LOG_AREA.with(logLines(commits, ZoneId.systemDefault()).mkString("\n")).show(title));
                })
                .getOrElse(() -> loadingPanel(Messages.get("panel.log.title")));
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
            List<String> lines = List.of(Messages.get("content.commitSha", c.shortSha()),
                            Messages.get("content.author", c.authorName(), c.authorEmail()),
                            dateTime.format(c.authorTime().atZone(zone)), "")
                    .appendAll(messageLines(c))
                    .append("");
            // Like git's graph: a merge opens a rail with a "\\" line, the last commit of a merged
            // branch closes it with a "/" in place of its second line.
            String closing = "| ".repeat(Math.max(0, node.depth() - 1));
            return c.isMerge()
                    ? List.of(rails + "*   " + lines.head(),
                            rails + "|\\  " + Messages.get("content.merge", c.parents().map(Sha::shortSha).mkString(" ")))
                    .appendAll(lines.tail().map(line -> rails + "| | " + line))
                    : lines.zipWithIndex((line, i) -> i == 0 ? rails + "* " + line
                    : node.closesBranch() ? closing + (i == 1 ? "|/  " : "|   ") + line
                    : rails + "| " + line);
        });
    }

    /**
     * The indented message of {@code commit}: its subject, and its body after a blank line if it has one.
     */
    private static List<String> messageLines(Commit commit) {
        return List.of(MESSAGE_INDENT + commit.message())
                .appendAll(commit.body().isBlank() ? List.empty() : lines(commit.body())
                        .map(line -> line.isBlank() ? line : MESSAGE_INDENT + line)
                        .prepend(""));
    }

    /**
     * What the commit area shows: the commit, and what it changed.
     */
    private record CommitContent(Commit commit, String changes) {
    }

    private static StyledElement<?> commitDiffView(Model model) {
        String title = Messages.get("panel.commit.title");
        return model.commitDetail()
                .map(Preview::shown)
                .<StyledElement<?>>map(detail -> whenLoaded(title, detail.value(), changes -> commit(model, detail.key())
                        .<StyledElement<?>>map(commit -> COMMIT_AREA
                                .with(new CommitContent(commit, changes), () -> commitText(commit, changes))
                                .show(Messages.get("panel.commit.titleFor", commit.shortSha())))
                        .getOrElse(() -> loadingPanel(title))))
                .getOrElse(() -> model.commits().toOption().exists(List::isEmpty)
                        ? panel(title, text(Messages.get("content.noCommits")))
                        : loadingPanel(title));
    }

    private static Option<Commit> commit(Model model, Sha sha) {
        return model.commits().getOrElse(List.empty()).find(c -> c.sha().equals(sha));
    }

    /**
     * Like {@code git show}: the commit's header, its indented message, a {@code ---} line and then the changes.
     */
    static Text commitText(Commit commit, String changes) {
        DateTimeFormatter commitDate = DateTimeFormatter.ofPattern(Messages.get("content.commitDatePattern"), Locale.ENGLISH);
        List<String> message = messageLines(commit);
        return styledText(List.of(
                        styled(Messages.get("content.commitSha", commit.sha().value()), Theme.Classes.SHA),
                        Span.raw(Messages.get("content.commitAuthor", commit.authorName(), commit.authorEmail())),
                        Span.raw(Messages.get("content.commitDate", commitDate.format(
                                commit.authorTime().atZone(ZoneId.systemDefault())))),
                        Span.raw(""),
                        styled(message.head(), Theme.Classes.BOLD))
                .map(Line::from)
                .appendAll(message.tail().map(line -> Line.from(Span.raw(line))))
                .append(Line.from(Span.raw("---")))
                .appendAll(lines(changes).map(ContentPanel::diffLine)));
    }

    private static List<String> lines(String text) {
        return List.ofAll(text.lines()::iterator);
    }

    private static Text styledText(List<Line> lines) {
        return Text.from(lines.toJavaList());
    }

    private static Span styled(String text, String cssClass) {
        return Span.styled(text, Theme.spanStyle(cssClass));
    }

    private static StyledElement<?> loadingPanel(String title) {
        return dimPanel(title, Placeholders.loading());
    }

    private static StyledElement<?> dimPanel(String title, String content) {
        return panel(title, text(content).addClass(Theme.Classes.DIM));
    }

    private static <T> StyledElement<?> whenLoaded(String title, Loadable<T> loadable, Function<T, StyledElement<?>> view) {
        return switch (loadable) {
            case Loadable.Loaded<T>(T value) -> view.apply(value);
            case Loadable.Loading<T>() -> loadingPanel(title);
            case Loadable.Failed<T>(Failure failure) ->
                    panel(title, text(Placeholders.error(failure)).addClass(Theme.Classes.ERROR));
        };
    }
}
