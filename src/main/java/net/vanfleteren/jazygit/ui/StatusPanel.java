package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TextElement;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Failure;
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
            case Loadable.Failed<RepoStatus>(Failure failure) -> Placeholders.error(failure);
            case Loadable.Loaded<RepoStatus>(RepoStatus status) -> branchLabel(status.head());
        };
        TextElement repository = text(Messages.get("status.line", model.repositoryName(), branch));
        return panel(Messages.get("panel.status.title"), repository)
                .length(HEIGHT);
    }

    /**
     * The checked-out branch, or a description of the detached HEAD.
     */
    static String branchLabel(Head head) {
        return switch (head) {
            case Head.OnBranch(Branch.Name branch, Option<Sha> tip) -> branch.value();
            case Head.Detached(Sha at) -> Messages.get("status.headDetached", at.shortSha());
        };
    }
}
