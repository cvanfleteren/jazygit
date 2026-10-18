package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.feature.github.browse.BrowseMsg;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for opening branches on GitHub.
 */
public final class BrowseKeys {

    private BrowseKeys() {}

    public static Keymap register(Keymap keys) {
        return Scopes.PULL_REQUESTS.foldLeft(
                keys.add(Scopes.BRANCHES, Binding.onSelected("O", "help.branches.browse", BrowseMsg.Requested::new))
                        .add(
                                Scopes.REMOTE_BRANCHES,
                                Binding.onSelected("O", "help.branches.browse", BrowseMsg.RemoteRequested::new)),
                (added, scope) -> added.add(
                        scope, Binding.onSelected("O", "help.prs.browse", BrowseMsg.PullRequestRequested::new)));
    }
}
