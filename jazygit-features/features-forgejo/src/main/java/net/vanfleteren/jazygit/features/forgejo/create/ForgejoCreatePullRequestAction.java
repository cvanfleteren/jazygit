package net.vanfleteren.jazygit.features.forgejo.create;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

/**
 * Creating the pull request of a branch on Forgejo (ADR 178), which asking for the pull request of a branch without
 * one leads to: its popups, on no key of its own.
 */
public final class ForgejoCreatePullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofPopups(ForgejoCreatePullRequestDialogs::popups);

    private ForgejoCreatePullRequestAction() {}
}
