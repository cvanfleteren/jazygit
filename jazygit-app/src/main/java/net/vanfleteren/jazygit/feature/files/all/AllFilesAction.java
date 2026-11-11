package net.vanfleteren.jazygit.feature.files.all;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Switching the files pane between the changed files of the working tree and all its files (ADR 108).
 */
public final class AllFilesAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.FILES, Binding.always("a", "help.files.allFiles", AllFilesMsg.Toggled::new)));

    private AllFilesAction() {}
}
