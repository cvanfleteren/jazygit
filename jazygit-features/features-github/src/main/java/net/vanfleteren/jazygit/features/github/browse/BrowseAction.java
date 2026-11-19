package net.vanfleteren.jazygit.features.github.browse;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Opening the selected branch, or pull request, on GitHub in the browser.
 */
public final class BrowseAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> Scopes.PULL_REQUESTS.foldLeft(
            keys.add(Scopes.BRANCHES, Binding.onSelected("O", "github.help.branches.browse", BrowseMsg.Requested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected("O", "github.help.branches.browse", BrowseMsg.RemoteRequested::new)),
            (added, scope) -> added.add(
                    scope, Binding.onSelected("O", "github.help.prs.browse", BrowseMsg.PullRequestRequested::new))));

    private BrowseAction() {}
}
