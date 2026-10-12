package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TextElement;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Top-left panel showing the repository name and the current branch. It is display-only and does not
 * take part in focus cycling.
 */
public final class StatusPanel {

    /** Rows of the panel, borders included. */
    public static final int HEIGHT = 3;

    private StatusPanel() {
    }

    public static Panel render(Model model) {
        String branch = switch (model.status()) {
            case Loadable.Loading<RepoStatus>() -> Placeholders.loading();
            case Loadable.Failed<RepoStatus>(String message) -> Placeholders.error(message);
            case Loadable.Loaded<RepoStatus>(RepoStatus status) -> status.branchLabel();
        };
        TextElement repository = text(Messages.get("status.line", model.repositoryName(), branch));
        return panel(Messages.get("panel.status.title"), repository)
                .length(HEIGHT);
    }
}
