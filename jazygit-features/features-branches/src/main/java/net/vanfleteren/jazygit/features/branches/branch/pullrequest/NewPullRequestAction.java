package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

/**
 * Creating a pull request, as far as it is the same on every forge: its popups, on no key of its own. Each forge's
 * key leads to it.
 */
public final class NewPullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofPopups(NewPullRequestDialogs::popups);

    private NewPullRequestAction() {}
}
