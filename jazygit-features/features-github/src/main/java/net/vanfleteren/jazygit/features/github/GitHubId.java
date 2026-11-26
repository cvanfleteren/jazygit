package net.vanfleteren.jazygit.features.github;

import net.vanfleteren.jazygit.kernel.state.FeatureId;

/**
 * The id of GitHub's feature, by which it is on or off for a repository (ADR 171), as {@link GitHubFeature#ID}. It is
 * kept apart so that what the feature is built of, as the screen of the pull requests, can name it without
 * initialising the feature, which is built of them in turn.
 */
public final class GitHubId {

    public static final FeatureId ID = new FeatureId("github");

    private GitHubId() {}
}
