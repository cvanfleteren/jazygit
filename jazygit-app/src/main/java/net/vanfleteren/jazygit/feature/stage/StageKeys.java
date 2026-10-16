package net.vanfleteren.jazygit.feature.stage;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for staging and unstaging.
 */
public final class StageKeys {

    private StageKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                Scopes.FILES,
                Binding.onSelected(
                        " ",
                        "help.files.stage",
                        node -> new StageMsg.Requested(
                                node.entries(),
                                node instanceof FileTree.Dir dir ? Option.of(dir.path()) : Option.none())));
    }
}
