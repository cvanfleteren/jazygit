package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;

/**
 * Top-left panel showing the repository name and the current branch. It is display-only and
 * does not take part in focus cycling.
 */
public final class StatusPanel {

    private StatusPanel() {
    }

    public static Panel render(Model model) {
        String branch = switch (model.status()) {
            case Loadable.Loading<RepoStatus>() -> Placeholders.LOADING;
            case Loadable.Failed<RepoStatus>(String message) -> Placeholders.error(message);
            case Loadable.Loaded<RepoStatus>(RepoStatus status) -> status.branchLabel();
        };
        return panel("Status", text(model.repositoryName() + " → " + branch))
                .rounded()
                .borderColor(Color.WHITE)
                .length(3);
    }
}
