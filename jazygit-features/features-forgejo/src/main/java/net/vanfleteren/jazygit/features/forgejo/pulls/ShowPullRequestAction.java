package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Showing the pull request on Forgejo of the selected branch (ADR 176), on the key GitHub shows its own on (ADR 175).
 */
public final class ShowPullRequestAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES,
                    Binding.onSelected("o", "forgejo.help.branches.pullRequest", PullRequestMsg.Requested::new)),
            dispatch -> List.of(PopupSlot.returningToFocus(new ForgejoPullRequestDialog(dispatch))));

    private ShowPullRequestAction() {}
}
