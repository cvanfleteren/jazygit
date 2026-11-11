package net.vanfleteren.jazygit.feature.files.filter;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.view.FileFilterMsg;

/**
 * Filtering the files of the files pane by their path, of the working tree and of the changes open in its place
 * (ADR 105). The files pane takes the keys of the line the filter is typed in, and Escape to clear it, itself.
 */
public final class FileFilterAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
                    Scopes.FILES, Binding.always("/", "help.files.filter", FileFilterMsg.Opened::new))
            .add(Scopes.COMMIT_FILES, Binding.always("/", "help.commitFiles.filter", FileFilterMsg.Opened::new)));

    private FileFilterAction() {}
}
