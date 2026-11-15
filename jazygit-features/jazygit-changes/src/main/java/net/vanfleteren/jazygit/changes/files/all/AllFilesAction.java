package net.vanfleteren.jazygit.changes.files.all;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Switching the files pane between the changed files of the working tree and all its files (ADR 108).
 */
public final class AllFilesAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys ->
            keys.add(Scopes.FILES, Binding.always("a", "changes.help.files.allFiles", AllFilesMsg.Toggled::new)));

    private AllFilesAction() {}
}
