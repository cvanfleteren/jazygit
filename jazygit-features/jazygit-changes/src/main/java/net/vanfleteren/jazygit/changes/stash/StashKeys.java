package net.vanfleteren.jazygit.changes.stash;

import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * The keys for stashing the changes, and for popping and dropping a stash.
 */
public final class StashKeys {

    private StashKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(Scopes.FILES, Binding.always("s", "changes.help.files.stash", StashMsg.Requested::new))
                .add(
                        Scopes.STASH,
                        Binding.onSelected("g", "changes.help.stash.pop", StashMsg.PopRequested::new),
                        Binding.onSelected("d", "changes.help.stash.drop", StashMsg.DropRequested::new));
    }
}
