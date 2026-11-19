package net.vanfleteren.jazygit.features.changes.discard;

import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The key for discarding changes.
 */
public final class DiscardKeys {

    private DiscardKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                Scopes.FILES,
                Binding.onSelected(
                        "d", "changes.help.files.discard", node -> new DiscardMsg.Requested(node.entries())));
    }
}
