package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forge.checks.RerunDialog;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * Showing the checks on the tip of the selected branch, local or remote, one by one (ADR 32).
 */
public final class ShowChecksAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> ChecksDialog.KEYS
                    .apply(keys)
                    .add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "C",
                                    "github.help.branches.checks",
                                    name -> new ChecksMsg.Requested(GitHubChecksPort.PORT, new BranchRef.Local(name))))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "C",
                                    "github.help.remoteBranches.checks",
                                    remote -> new ChecksMsg.Requested(
                                            GitHubChecksPort.PORT, new BranchRef.Remote(remote)))),
            dispatch -> List.of(
                    PopupSlot.returningToFocus(new ChecksDialog(dispatch)),
                    PopupSlot.returningToFocus(new ConfirmDialog<>(RerunDialog.SPEC, dispatch))));

    private ShowChecksAction() {}
}
