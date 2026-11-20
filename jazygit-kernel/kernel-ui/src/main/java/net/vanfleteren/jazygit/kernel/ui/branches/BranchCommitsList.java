package net.vanfleteren.jazygit.kernel.ui.branches;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.BranchCommitsView;
import net.vanfleteren.jazygit.kernel.ui.commits.AheadView;
import net.vanfleteren.jazygit.kernel.ui.commits.CommitRows;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.Memo;
import net.vanfleteren.jazygit.kernel.ui.widgets.Pane;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The commits of a branch, local or remote, which the branches pane lists in place of the branches while they are
 * open: drawn as the commits pane draws those of HEAD, with the commits on the base of the branch dimmed. The keys
 * only move through them; Enter moves the focus to the highlighted commit.
 */
class BranchCommitsList {

    private final LoadableList<List<Commit>> list;
    private final Keymap keymap = Keymap.standard();
    // Which branch was listed at the last render.
    private Option<BranchRef> shown = Option.none();
    // The rows of the commits listed, worked out again only when what they are drawn from changes, as the commits pane
    // does.
    private final Memo<Tuple3<List<Commit>, Option<Sha>, AheadView>, List<LoadableList.Item>> drawn = new Memo<>();

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
        Option<BranchCommitsView> open = model.repoView().branches().branchCommits();
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
        Option<Sha> copied = model.repoView().commits().cherryPick().map(Commit::sha);
        AheadView ahead =
                new AheadView(AheadUpdate.branch(model, view.branch()), AheadUpdate.distance(model, view.branch()));
        return list.renderItems(
                view.commits(model),
                commits ->
                        drawn.get(Tuple.of(commits, copied, ahead), key -> CommitRows.graphed(commits, copied, ahead)),
                focused,
                active,
                Pane.title(ahead.title(view.branch().name())),
                Option.none(),
                event -> {});
    }

    /**
     * The rows the last render showed.
     */
    List<LoadableList.Row> shownRows() {
        return list.shownRows();
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
