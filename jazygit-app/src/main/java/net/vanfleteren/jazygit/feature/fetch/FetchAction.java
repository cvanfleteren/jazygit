package net.vanfleteren.jazygit.feature.fetch;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Fetching from the remotes when the user asks, from the files and the branches, whether the timer fetches or not
 * (ADR 125).
 */
public final class FetchAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.FILES, Binding.always("f", "help.files.fetch", FetchMsg.Requested::new))
                    .add(Scopes.BRANCHES, Binding.always("f", "help.branches.fetch", FetchMsg.Requested::new)));

    private FetchAction() {}
}
