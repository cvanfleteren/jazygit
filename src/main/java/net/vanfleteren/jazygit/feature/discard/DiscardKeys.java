package net.vanfleteren.jazygit.feature.discard;

import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The key for discarding changes.
 */
public final class DiscardKeys {

    private DiscardKeys() {
    }

    public static void register(Keymap.Builder keys) {
        keys.add(Scopes.FILES, Binding.<FileTree>onSelected("d", "help.files.discard",
                node -> new DiscardMsg.Requested(node.entries())));
    }
}
