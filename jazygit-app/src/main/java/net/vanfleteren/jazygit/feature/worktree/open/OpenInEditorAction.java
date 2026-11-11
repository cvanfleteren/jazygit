package net.vanfleteren.jazygit.feature.worktree.open;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;

/**
 * Opening the selected worktree, or the worktree of the selected branch, in an editor the user set (ADR 110).
 */
public final class OpenInEditorAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.WORKTREES,
                            Binding.onSelected(
                                    "e",
                                    "help.worktrees.openInEditor",
                                    worktree -> new OpenInEditorMsg.WorktreeRequested(worktree.location())))
                    .add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "e", "help.branches.openInEditor", OpenInEditorMsg.BranchRequested::new)),
            dispatch -> List.of(PopupSlot.returningToFocus(new PickDialog<>(OpenInEditorDialog.MENU, dispatch))));

    private OpenInEditorAction() {}
}
