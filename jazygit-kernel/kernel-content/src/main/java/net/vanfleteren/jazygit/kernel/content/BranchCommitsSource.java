package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;

/**
 * The commit highlighted in the commits of a branch the branches pane lists.
 */
record BranchCommitsSource() implements ContentSource {

    /**
     * The commit highlighted in the commits of a branch the branches pane lists.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.BRANCH_COMMIT, BranchesPanel.PANE);

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        if (pane != Mirrored.BRANCHES) {
            return Option.none();
        }
        return model.repoView()
                .branches()
                .branchCommits()
                .map(view -> CommitSource.commit(
                        model,
                        AREA,
                        view.detail(),
                        sha -> model.branchCommit(view, sha),
                        Option.when(view.commits(model).toOption().exists(List::isEmpty), "content.noCommits")));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }
}
