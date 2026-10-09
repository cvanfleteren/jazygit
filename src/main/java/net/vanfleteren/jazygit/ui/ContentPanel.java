package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;

import java.util.List;
import java.util.function.Function;

/**
 * The right-hand content panel: it always mirrors whichever left pane currently has focus,
 * showing the file status, the branch log, or the commit diff/body.
 */
public final class ContentPanel {

    private ContentPanel() {
    }

    public static Panel render(Model model, String focusedId, int branchesSelection, int commitsSelection) {
        if (BranchesPanel.ID.equals(focusedId)) {
            return branchLogView(model, branchesSelection);
        }
        if (CommitsPanel.ID.equals(focusedId)) {
            return commitDiffView(model, commitsSelection);
        }
        return fileStatusView(model);
    }

    private static Panel fileStatusView(Model model) {
        return whenLoaded("Status", model.status(), status -> panel("Status", rows(status.files().stream()
                .map(f -> f.type().marker() + " " + f.path())
                .toList())).rounded());
    }

    private static Panel branchLogView(Model model, int branchesSelection) {
        return whenLoaded("Log", model.branches(), branches -> {
            if (branches.isEmpty()) {
                return panel("Log", text("No branches")).rounded();
            }
            Branch branch = branches.get(clamp(branchesSelection, branches.size()));
            return whenLoaded("Log: " + branch.name(), model.commits(), commits ->
                    panel("Log: " + branch.name(), rows(commits.stream()
                            .map(c -> c.shortSha() + "  " + c.date() + "  " + c.message())
                            .toList())).rounded());
        });
    }

    private static Panel commitDiffView(Model model, int commitsSelection) {
        return whenLoaded("Commit", model.commits(), commits -> {
            if (commits.isEmpty()) {
                return panel("Commit", text("No commits")).rounded();
            }
            Commit commit = commits.get(clamp(commitsSelection, commits.size()));
            return panel("Commit: " + commit.shortSha(),
                    text(commit.message()).bold(),
                    text(commit.author() + " on " + commit.date()).dim(),
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
