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
    // What a commit or a stash changed in the files highlighted in the tree of them the files pane lists.
    COMMIT_FILE("commit-file-changes", FilesPanel.ID),
    // The commit highlighted in the divergence the branches pane lists.
    DIVERGENCE("divergence-commit", BranchesPanel.ID),
    // The commit highlighted in the commits of a branch the branches pane lists.
    BRANCH_COMMIT("branch-commit", BranchesPanel.ID),
    // It shows the pull request highlighted in whichever list of the pull requests screen is in use.
    PULL_REQUEST(
            "pull-request-preview", model -> model.prOverview().listPane().getOrElse(PullRequestsPanel.FIRST_PANE_ID)),
    LOG("branch-log", BranchesPanel.ID);

    private final String id;
    private final Function<Model, String> pane;

    ContentArea(String id, String pane) {
        this(id, model -> pane);
    }

    ContentArea(String id, Function<Model, String> pane) {
        this.id = id;
        this.pane = pane;
    }

    String id() {
        return id;
    }

    /**
     * The area with id {@code id}, which can have the focus, if it is one.
     */
    static Option<ContentArea> focusable(String id) {
        return List.of(values()).find(area -> area.id.equals(id));
    }

    /**
     * The pane whose highlighted row the area shows in {@code model}, which gets the focus back when Escape, or Left
     * once the area is scrolled all the way left, is pressed in it.
     */
    String pane(Model model) {
        return pane.apply(model);
    }
}
