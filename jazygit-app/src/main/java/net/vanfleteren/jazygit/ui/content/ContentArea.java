package net.vanfleteren.jazygit.ui.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.github.PullRequestsPanel;
import net.vanfleteren.jazygit.ui.stash.StashPanel;

/**
 * A scrollable area of the content panel, and the pane whose highlighted row it shows.
 */
public enum ContentArea {
    STAGED(AreaId.STAGED, FilesPanel.ID),
    UNSTAGED(AreaId.UNSTAGED, FilesPanel.ID),
    COMMIT(AreaId.COMMIT, CommitsPanel.ID),
    STASH(AreaId.STASH, StashPanel.ID),
    // What a commit or a stash changed in the files highlighted in the tree of them the files pane lists.
    COMMIT_FILE(AreaId.COMMIT_FILE, FilesPanel.ID),
    // A file without changes highlighted among all files of the working tree, as it is on disk (ADR 108).
    FILE_CONTENT(AreaId.FILE_CONTENT, FilesPanel.ID),
    // What the files pane shows in place of a diff or a file's content, which holds the focus while the files are
    // walked from the content panel (ADR 117).
    FILE_NOTE(AreaId.FILE_NOTE, FilesPanel.ID),
    // The commit highlighted in the divergence the branches pane lists.
    DIVERGENCE(AreaId.DIVERGENCE, BranchesPanel.ID),
    // The commit highlighted in the commits of a branch the branches pane lists.
    BRANCH_COMMIT(AreaId.BRANCH_COMMIT, BranchesPanel.ID),
    // It shows the pull request highlighted in whichever list of the pull requests screen is in use.
    PULL_REQUEST(
            AreaId.PULL_REQUEST,
            model -> model.prOverview().listPane().getOrElse(() -> new PaneId(PullRequestsPanel.FIRST_PANE_ID))),
    LOG(AreaId.LOG, BranchesPanel.ID);

    private final AreaId id;
    private final Function<Model, PaneId> pane;

    ContentArea(AreaId id, String pane) {
        this(id, model -> new PaneId(pane));
    }

    ContentArea(AreaId id, Function<Model, PaneId> pane) {
        this.id = id;
        this.pane = pane;
    }

    public AreaId id() {
        return id;
    }

    /**
     * The area with id {@code id}, which can have the focus, if it is one.
     */
    static Option<ContentArea> focusable(String id) {
        return List.of(values()).find(area -> area.id.value().equals(id));
    }

    /**
     * The pane whose highlighted row the area shows in {@code model}, which gets the focus back when Escape, or Left
     * once the area is scrolled all the way left, is pressed in it.
     */
    PaneId pane(Model model) {
        return pane.apply(model);
    }
}
