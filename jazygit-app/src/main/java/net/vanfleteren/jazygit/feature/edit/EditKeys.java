package net.vanfleteren.jazygit.feature.edit;

import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Msg;

/**
 * The keys for opening files in the editor.
 */
public final class EditKeys {

    private EditKeys() {}

    public static Keymap register(Keymap keys) {
        // A directory is not opened: not every editor opens one.
        return keys.add(
                Scopes.FILES,
                new Binding<>(
                        "e",
                        "help.files.edit",
                        selection -> selection
                                .filter(FileTree.File.class::isInstance)
                                .map(FileTree.File.class::cast)
                                .<Msg>map(file ->
                                        new EditMsg.Requested(file.entry().path()))));
    }
}
