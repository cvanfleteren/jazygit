package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for opening branches on GitHub.
 */
public final class BrowseKeys {

    private BrowseKeys() {
    }

    public static Keymap register(Keymap keys) {
        return keys
                .add(Scopes.BRANCHES,
                        Binding.<Branch.Name>onSelected("O", "help.branches.browse", BrowseMsg.Requested::new))
                .add(Scopes.REMOTE_BRANCHES,
                        Binding.<RemoteBranch>onSelected("O", "help.branches.browse", BrowseMsg.RemoteRequested::new));
    }
}
