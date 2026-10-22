package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;

/**
 * The worktrees of the repository, the one shown marked. Space switches to the highlighted one, d removes it, L
 * locks or unlocks it, and p forgets the ones whose directory is gone.
 */
class WorktreesList implements BranchesTabList {

    private final LoadableList<List<Worktree>> list;
    private final Keymap keymap = Keymap.standard();

    /**
     * @param id         the id of the pane the worktrees are listed in
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param highlights how the highlighted worktree is shown, also when the pane is not in use
     */
    WorktreesList(String id, Consumer<Msg> dispatch, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get("panel.worktrees.title"), id, LoadableList.Reselect.following(Worktree::path), highlights);
        list.highlightSymbol("");
        list.whenEmpty(Messages.get("worktrees.empty"));
        list.onKey(event -> PaneKeys.sending(keymap.handle(Scopes.WORKTREES, event, selectedWorktree()), dispatch));
    }

    /**
     * A worktree of {@code repo}: marked when it is the one shown, the name of its directory, marked when it has
     * changes, and what it has checked out, with how far its branch is from its upstream; then whether it is the
     * main one, locked, or gone from disk.
     */
    static LoadableList.Row row(Repo repo, Worktree worktree) {
        String checkedOut = worktree.branch()
                .map(Branch.Name::value)
                .getOrElse(() -> Messages.get(
                        "worktrees.detached", worktree.head().map(Sha::shortSha).getOrElse("")));
        return new LoadableList.Row(List.of(
                        Option.of(LoadableList.Seg.of((worktree.current() ? "* " : "  ") + worktree.name() + " ")),
                        Option.when(
                                repo.hasChanges(worktree).contains(true),
                                () -> LoadableList.Seg.styled("● ", Theme.Classes.UNSTAGED)),
                        Option.of(
                                worktree.branch().isDefined()
                                        ? LoadableList.Seg.of(checkedOut)
                                        : LoadableList.Seg.styled(checkedOut, Theme.Classes.DIM)))
                .flatMap(segment -> segment)
                .appendAll(worktree.branch()
                        .flatMap(repo::branch)
                        .map(BranchSegs::sync)
                        .getOrElse(List.empty()))
                .appendAll(List.of(
                                Option.when(
                                        worktree.main(),
                                        () -> LoadableList.Seg.styled(
                                                " " + Messages.get("worktrees.main"), Theme.Classes.DIM)),
                                worktree.locked()
                                        .map(reason -> LoadableList.Seg.styled(
                                                " "
                                                        + (reason.isBlank()
                                                                ? Messages.get("worktrees.locked")
                                                                : Messages.get("worktrees.lockedBecause", reason)),
                                                Theme.Classes.DIM)),
                                Option.when(
                                        worktree.prunable(),
                                        () -> LoadableList.Seg.styled(
                                                " " + Messages.get("worktrees.missing"), Theme.Classes.GONE)))
                        .flatMap(segment -> segment)));
    }

    /**
     * Where among the worktrees the one an open popup is about is, once they list it: the one the user acts on
     * next, as the one just added. Only a popup moves the highlight; a worktree added outside the app does not.
     */
    static Option<Integer> asked(Model model) {
        return model.popup(WorktreePopup.class)
                .flatMap(popup -> model.repo()
                        .worktrees()
                        .toOption()
                        .map(worktrees -> worktrees.indexWhere(
                                worktree -> worktree.location().equals(popup.location()))))
                .filter(index -> index >= 0);
    }

    @Override
    public Panel render(Model model, boolean focused, boolean active, Line title) {
        Panel panel = list.render(
                model.repo().worktrees(),
                all -> all.map(worktree -> row(model.repo(), worktree)),
                focused,
                active,
                title);
        // Highlighted when drawn, after the render followed the highlighted one.
        asked(model).forEach(list::select);
        return panel;
    }

    @Override
    public int selectedIndex() {
        return list.selectedIndex();
    }

    /**
     * The branch the highlighted worktree has checked out; a detached one leaves the log as it is.
     */
    @Override
    public Option<BranchRef> selectedRef() {
        return selectedWorktree().flatMap(Worktree::branch).map(BranchRef.Local::new);
    }

    /**
     * The highlighted worktree, once the worktrees are shown.
     */
    Option<Worktree> selectedWorktree() {
        return list.selected(worktrees -> worktrees);
    }
}
