package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.Diffs;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The right-hand content panel: it always mirrors whichever left pane currently has focus,
 * showing the file diff, the branch log, or the commit diff/body.
 */
public final class ContentPanel {

    private ContentPanel() {
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        return model.fileDiff()
                .map(ContentPanel::shown)
                .map(d -> switch (d.diff()) {
                    case Loadable.Loaded<Diffs>(Diffs diffs) -> diffPanels(diffs);
                    case Loadable.Loading<Diffs>() -> panel("Diff", text(Placeholders.LOADING).dim()).rounded();
                    case Loadable.Failed<Diffs>(String message) ->
                            panel("Diff", text(Placeholders.error(message))).rounded();
                })
                .orElseGet(() -> panel("Diff", text(Placeholders.LOADING).dim()).rounded());
    }

    private static StyledElement<?> diffPanels(Diffs diffs) {
        if (diffs.isEmpty()) {
            return panel("Diff", text("No changes").dim()).rounded();
        }
        List<Element> panels = Stream.of(
                        Map.entry("Staged changes", diffs.staged()),
                        Map.entry("Unstaged changes", diffs.unstaged()))
                .filter(e -> !e.getValue().isBlank())
                .map(e -> (Element) panel(e.getKey(), e.getValue().lines().map(ContentPanel::diffLine)
                        .toArray(Element[]::new)).rounded().fill())
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
     * A line of a unified diff, colored by what it is: added, removed, a hunk header or a file header.
     */
    static Element diffLine(String line) {
        if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ")) {
            return text(line).bold();
        }
        if (line.startsWith("+")) {
            return text(line).fg(Color.GREEN);
        }
        if (line.startsWith("-")) {
            return text(line).fg(Color.RED);
        }
        if (line.startsWith("@@")) {
            return text(line).fg(Color.CYAN);
        }
        return text(line);
    }

    private static Panel branchLogView(Model model) {
        return model.branchLog()
                .map(ContentPanel::shown)
                .map(log -> whenLoaded("Log: " + log.branch(), log.commits(), commits ->
                        panel("Log: " + log.branch(), rows(logLines(commits, ZoneId.systemDefault()))).rounded()))
                .orElseGet(() -> panel("Log", text(Placeholders.LOADING).dim()).rounded());
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
                        Stream.of("commit " + c.shortSha(), author(c), DATE_TIME.format(c.authorTime().atZone(zone)),
                                "", c.message(), ""),
                        c.body().isBlank() ? Stream.empty() : Stream.concat(c.body().lines(), Stream.of(""))))
                .toList();
    }

    private static String author(Commit commit) {
        return commit.authorName() + " <" + commit.authorEmail() + ">";
    }

    private static Panel commitDiffView(Model model, int commitsSelection) {
        return whenLoaded("Commit", model.commits(), commits -> {
            if (commits.isEmpty()) {
                return panel("Commit", text("No commits")).rounded();
            }
            Commit commit = commits.get(clamp(commitsSelection, commits.size()));
            return panel("Commit: " + commit.shortSha(),
                    text(commit.message()).bold(),
                    text(author(commit) + " on " + DATE_TIME.format(commit.authorTime().atZone(ZoneId.systemDefault())))
                            .dim(),
                    spacer(),
                    text(commit.body()))
                    .rounded();
        });
    }


    private static <T> Panel whenLoaded(String title, Loadable<T> loadable, Function<T, Panel> view) {
        return switch (loadable) {
            case Loadable.Loaded<T>(T value) -> view.apply(value);
            case Loadable.Loading<T>() -> panel(title, text(Placeholders.LOADING).dim()).rounded();
            case Loadable.Failed<T>(String message) -> panel(title, text(Placeholders.error(message))).rounded();
        };
    }

    private static Element[] rows(List<String> lines) {
        return lines.stream().map(line -> (Element) text(line)).toArray(Element[]::new);
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size - 1));
    }
}
