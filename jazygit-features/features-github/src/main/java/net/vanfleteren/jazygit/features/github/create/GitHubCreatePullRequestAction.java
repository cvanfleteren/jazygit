package net.vanfleteren.jazygit.features.github.create;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

/**
 * Creating the pull request of a branch, which asking for the pull request of a branch without one leads to.
 */
public final class GitHubCreatePullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofPopups(GitHubCreatePullRequestDialogs::popups);

    private GitHubCreatePullRequestAction() {}
}
