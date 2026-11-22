package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

/**
 * Creating the pull request of a branch on Forgejo (ADR 178), which asking for the pull request of a branch without
 * one leads to: its popups, on no key of its own.
 */
public final class CreatePullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofPopups(CreatePullRequestDialogs::popups);

    private CreatePullRequestAction() {}
}
