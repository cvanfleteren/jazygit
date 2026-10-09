package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.i18n.Messages;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TextElement;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;

/**
 * Top-left panel showing the repository name and the current branch, and why the last operation
 * failed, if it did. It is display-only and
 * does not take part in focus cycling.
 */
public final class StatusPanel {

    private StatusPanel() {
    }

    public static Panel render(Model model) {
        String branch = switch (model.status()) {
            case Loadable.Loading<RepoStatus>() -> Placeholders.loading();
            case Loadable.Failed<RepoStatus>(String message) -> Placeholders.error(message);
            case Loadable.Loaded<RepoStatus>(RepoStatus status) -> status.branchLabel();
        };
        TextElement repository = text(Messages.get("status.line", model.repositoryName(), branch));
        return model.error()
                .map(error -> panel(Messages.get("panel.status.title"), repository, text(error).red()).length(4))
                .orElseGet(() -> panel(Messages.get("panel.status.title"), repository).length(3))
                .rounded()
                .borderColor(Color.WHITE);
    }
}
