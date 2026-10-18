package net.vanfleteren.jazygit.feature.github.create;

import net.vanfleteren.jazygit.feature.FeatureAction;

/**
 * Creating the pull request of a branch, which asking for the pull request of a branch without one leads to.
 */
public final class CreatePullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofPopups(CreatePullRequestDialogs::popups);

    private CreatePullRequestAction() {}
}
