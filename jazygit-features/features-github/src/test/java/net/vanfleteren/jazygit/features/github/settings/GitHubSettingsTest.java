package net.vanfleteren.jazygit.features.github.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The GitHub settings as the settings file keeps them (ADR 163).
 */
class GitHubSettingsTest {

    @Test
    void theBrowsersAreKeptUnderThePathOfTheirKey() {
        AccountBrowsers browsers = AccountBrowsers.NONE.with("github.com", "cvf", Option.some("firefox"));

        Map<List<String>, Object> kept = GitHubSettings.BROWSERS.codec().encode("elsewhere.browsers", browsers);

        assertEquals(
                List.of(List.of("elsewhere", "browsers", "github.com", "cvf")),
                kept.keySet().toList());
    }

    @Test
    void theBrowsersAreKeptUnderGitHubBrowsers() {
        AccountBrowsers browsers = AccountBrowsers.NONE.with("github.com", "cvf", Option.some("firefox"));

        Map<List<String>, Object> kept =
                GitHubSettings.BROWSERS.codec().encode(GitHubSettings.BROWSERS.path(), browsers);

        assertEquals(
                List.of(List.of("github", "browsers", "github.com", "cvf")),
                kept.keySet().toList());
    }
}
