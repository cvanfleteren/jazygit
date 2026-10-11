package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

import java.util.Optional;

/**
 * The keys for staging and unstaging.
 */
public final class StageKeys {

    private StageKeys() {
    }

    public static void register(Keymap.Builder keys) {
        keys.add(Scopes.FILES, Binding.<FileTree>onSelected(" ", "help.files.stage",
                node -> new StageMsg.Requested(node.entries(),
                        node instanceof FileTree.Dir dir ? Optional.of(dir.path()) : Optional.empty())));
    }
}
