package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for stashing the changes, and for popping and dropping a stash.
 */
public final class StashKeys {

    private StashKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(Scopes.FILES, Binding.always("s", "help.files.stash", StashMsg.Requested::new))
                .add(
                        Scopes.STASH,
                        Binding.onSelected("g", "help.stash.pop", StashMsg.PopRequested::new),
                        Binding.onSelected("d", "help.stash.drop", StashMsg.DropRequested::new));
    }
}
