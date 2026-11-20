package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Launch;
import net.vanfleteren.jazygit.kernel.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import org.junit.jupiter.api.Test;

/**
 * What the features answer of what happens in the kernel, as the app wires it (ADR 166).
 */
class FeatureReactionsTest {

    @Test
    void openingTheRepositoryAsksForItsPullRequestsWithoutWaitingForTheFirstFetch() {
        Update.Step begun = Update.begin(
                "repo",
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                RepositoryTheme.NONE,
                Landing.START,
                Features.REACTIONS,
                Instant.parse("2026-10-22T10:00:00Z"));

        assertTrue(
                begun.launches().map(Launch::cmd).exists(LoadRepositoryPullRequests.class::isInstance),
                begun.launches().toString());
    }
}
