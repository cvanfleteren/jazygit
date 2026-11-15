package net.vanfleteren.jazygit.github.create;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;

/**
 * Creating the pull request of a branch, which asking for the pull request of a branch without one leads to.
 */
public final class CreatePullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofPopups(CreatePullRequestDialogs::popups);

    private CreatePullRequestAction() {}
}
