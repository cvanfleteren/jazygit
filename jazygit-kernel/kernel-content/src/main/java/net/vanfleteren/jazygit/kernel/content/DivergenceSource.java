package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Divergence;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The commit highlighted in the divergence the local branches pane lists.
 */
record DivergenceSource() implements ContentSource {

    /**
     * The commit highlighted in the divergence the branches pane lists.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.DIVERGENCE, Scopes.BRANCHES.pane());

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        if (!pane.equals(Mirrored.BRANCHES)) {
            return Option.none();
        }
        return model.repoView()
                .branches()
                .divergence()
                .filter(view -> model.repoView().branches().tab() == BranchesTab.LOCAL)
                .map(view -> CommitSource.commit(
                        model,
                        AREA,
                        view.detail(),
                        view::commit,
                        Option.when(view.commits().toOption().exists(Divergence::isEmpty), "content.noDivergence")));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }
}
