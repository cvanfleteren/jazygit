package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.features.github.DetectGitHub;
import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.features.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Launch;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import org.junit.jupiter.api.Test;

/**
 * What the features answer of what happens in the kernel, as the app wires it (ADR 158).
 */
class FeatureReactionsTest {

    @Test
    void openingTheRepositoryAsksWhetherGitHubAppliesAndItsPullRequestsAreReadOnceItDoes() {
        Update.Step begun = Update.begin(
                "repo",
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                RepositoryTheme.NONE,
                Landing.START,
                Features.REACTIONS,
                Instant.parse("2026-10-22T10:00:00Z"));

        // GitHub is off until it is found to apply (ADR 171).
        assertFalse(begun.model().on(GitHubFeature.ID));
        assertTrue(
                begun.launches().map(Launch::cmd).exists(DetectGitHub.class::isInstance),
                begun.launches().toString());
        assertFalse(begun.launches().map(Launch::cmd).exists(LoadRepositoryPullRequests.class::isInstance));

        // Once it is, the pull requests are read without waiting for the first fetch.
        Update.Next on = Update.update(begun.model(), new FeatureMsg.Applies(GitHubFeature.ID, true));
        assertTrue(on.model().on(GitHubFeature.ID));
        assertTrue(
                on.cmds().exists(LoadRepositoryPullRequests.class::isInstance),
                on.cmds().toString());
    }
}
