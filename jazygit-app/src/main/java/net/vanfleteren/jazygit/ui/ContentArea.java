package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.state.Model;

/**
 * A scrollable area of the content panel, and the pane whose highlighted row it shows.
 */
enum ContentArea {
    STAGED("diff-staged", FilesPanel.ID),
    UNSTAGED("diff-unstaged", FilesPanel.ID),
    COMMIT("commit-changes", CommitsPanel.ID),
    STASH("stash-changes", StashPanel.ID),
    // The commit highlighted in the divergence the branches pane lists.
    DIVERGENCE("divergence-commit", BranchesPanel.ID),
    // The commit highlighted in the commits of a branch the branches pane lists.
    BRANCH_COMMIT("branch-commit", BranchesPanel.ID),
    // It shows the pull request highlighted in whichever list of the pull requests screen is in use.
    PULL_REQUEST(
            "pull-request-preview",
            model -> model.prOverview().listPane().getOrElse(PullRequestsPanel.FIRST_PANE_ID),
            true),
    // Not focusable: clicking the log must not take the focus from the branches pane it mirrors.
    LOG("branch-log", model -> BranchesPanel.ID, false);

    private final String id;
    private final Function<Model, String> pane;
    private final boolean focusable;

    ContentArea(String id, String pane) {
        this(id, model -> pane, true);
    }

    ContentArea(String id, Function<Model, String> pane, boolean focusable) {
        this.id = id;
        this.pane = pane;
        this.focusable = focusable;
    }

    String id() {
        return id;
    }

    /**
     * The area with id {@code id} that can have the focus, if it is one.
     */
    static Option<ContentArea> focusable(String id) {
        return List.of(values()).find(area -> area.focusable && area.id.equals(id));
    }

    /**
     * Whether the area can have the focus, rather than only showing what its pane has highlighted.
     */
    boolean focusable() {
        return focusable;
    }

    /**
     * The pane whose highlighted row the area shows in {@code model}, which gets the focus back when Escape is
     * pressed in it.
     */
    String pane(Model model) {
        return pane.apply(model);
    }
}
