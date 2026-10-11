package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for stashing the changes, and for popping and dropping a stash.
 */
public final class StashKeys {

    private StashKeys() {
    }

    public static void register(Keymap.Builder keys) {
        keys.add(Scopes.FILES, Binding.<FileTree>always("s", "help.files.stash", StashMsg.Requested::new));
        keys.add(Scopes.STASH,
                Binding.<Stash>onSelected("g", "help.stash.pop", stash -> new StashMsg.PopRequested(stash.ref())),
                Binding.<Stash>onSelected("d", "help.stash.drop", stash -> new StashMsg.DropRequested(stash.ref())));
    }
}
