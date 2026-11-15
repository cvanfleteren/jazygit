package net.vanfleteren.jazygit.changes.fetch;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Fetching from the remotes when the user asks, from the files and the branches, whether the timer fetches or not
 * (ADR 125).
 */
public final class FetchAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.FILES, Binding.always("f", "changes.help.files.fetch", FetchMsg.Requested::new))
                    .add(Scopes.BRANCHES, Binding.always("f", "changes.help.branches.fetch", FetchMsg.Requested::new)));

    private FetchAction() {}
}
