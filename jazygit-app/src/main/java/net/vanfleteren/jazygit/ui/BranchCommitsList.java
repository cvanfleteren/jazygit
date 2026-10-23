package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.AheadUpdate;
import net.vanfleteren.jazygit.state.BranchCommitsView;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * The commits of a branch, local or remote, which the branches pane lists in place of the branches while they are
 * open: drawn as the commits pane draws those of HEAD, with the commits on the base of the branch dimmed. The keys
 * only move through them.
 */
class BranchCommitsList {

    private final LoadableList<List<Commit>> list;
    private final Keymap keymap = Keymap.standard();
    // Which branch was listed at the last render.
    private Option<BranchRef> shown = Option.none();

    /**
     * @param id         the id of the pane the commits are listed in
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param highlights how the highlighted commit is shown, also when the pane is not in use
     */
    BranchCommitsList(String id, Consumer<Msg> dispatch, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get("panel.branchCommits.title"),
                id,
                LoadableList.Reselect.following(Commit::sha),
                highlights);
        list.onKey(event -> PaneKeys.sending(keymap.handle(Scopes.BRANCH_COMMITS, event, selectedCommit()), dispatch));
    }

    /**
     * The pane listing the commits of the branch {@code model} has open, if it has. Called on every render, also
     * when none is open, so that the commits of a branch opened again start at its newest one.
     */
    Option<Panel> render(Model model, boolean focused, boolean active) {
        Option<BranchCommitsView> open = model.repoView().branchCommits();
        Option<BranchRef> branch = open.map(BranchCommitsView::branch);
        boolean opened = branch.isDefined() && !branch.equals(shown);
        shown = branch;
        return open.map(view -> {
            Panel panel = panel(model, view, focused, active);
            // After the render, which would have the highlight follow a commit of the branch listed before.
            if (opened) {
                list.select(0);
            }
            return panel;
        });
    }

    private Panel panel(Model model, BranchCommitsView view, boolean focused, boolean active) {
        Option<Sha> copied = model.repoView().cherryPick().map(Commit::sha);
        AheadView ahead = new AheadView(
                AheadUpdate.branch(model, view.branch()), model.repoView().onlyAhead());
        return list.render(
                view.commits(model).map(ahead::shown),
                commits -> CommitsPanel.marked(CommitsPanel.rows(CommitGraph.layout(commits), copied), commits, ahead),
                focused,
                active,
                Pane.title(ahead.title(view.branch().name())));
    }

    /**
     * The highlighted commit, once the commits are shown.
     */
    Option<Commit> selectedCommit() {
        return list.selected(commits -> commits);
    }

    /**
     * The message to send when the highlighted commit is not the one whose content {@code view} holds.
     */
    Option<Msg> selectionChange(BranchCommitsView view) {
        return PaneKeys.selectionChange(
                selectedCommit().map(Commit::sha), view.detail(), SelectionMsg.BranchCommitSelected::new);
    }
}
