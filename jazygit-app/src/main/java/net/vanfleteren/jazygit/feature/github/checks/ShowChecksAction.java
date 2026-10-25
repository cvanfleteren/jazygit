package net.vanfleteren.jazygit.feature.github.checks;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * Showing the checks on the tip of the selected branch, local or remote, one by one (ADR 32).
 */
public final class ShowChecksAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "c",
                                    "help.branches.checks",
                                    name -> new ChecksMsg.Requested(new BranchRef.Local(name))))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "c",
                                    "help.remoteBranches.checks",
                                    remote -> new ChecksMsg.Requested(new BranchRef.Remote(remote)))),
            dispatch -> List.of(PopupSlot.returningToFocus(new ChecksDialog(dispatch))));

    private ShowChecksAction() {}
}
