package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettingsPathsTest {

    private static final Path HOME = Path.of("/home/ada");

    private static Path configFile(String osName, Map<String, String> env) {
        return SettingsPaths.configFile(osName, env::get, HOME);
    }

    private static Path stateFile(String osName, Map<String, String> env) {
        return SettingsPaths.stateFile(osName, env::get, HOME);
    }

    @Test
    void linuxUsesTheXdgConfigHome() {
        assertEquals(Path.of("/xdg/jazygit/config.toml"), configFile("Linux", HashMap.of("XDG_CONFIG_HOME", "/xdg")));
    }

    @Test
    void linuxFallsBackToDotConfig() {
        assertEquals(Path.of("/home/ada/.config/jazygit/config.toml"), configFile("Linux", HashMap.empty()));
    }

    @Test
    void aRelativeOrEmptyXdgConfigHomeIsIgnored() {
        Path fallback = Path.of("/home/ada/.config/jazygit/config.toml");

        assertEquals(fallback, configFile("Linux", HashMap.of("XDG_CONFIG_HOME", "relative/dir")));
        assertEquals(fallback, configFile("Linux", HashMap.of("XDG_CONFIG_HOME", "")));
    }

    @Test
    void macOsUsesApplicationSupport() {
        assertEquals(
                Path.of("/home/ada/Library/Application Support/jazygit/config.toml"),
                configFile("Mac OS X", HashMap.empty()));
    }

    @Test
    void macOsUsesTheXdgConfigHomeWhenItIsSet() {
        assertEquals(
                Path.of("/xdg/jazygit/config.toml"), configFile("Mac OS X", HashMap.of("XDG_CONFIG_HOME", "/xdg")));
    }

    @Test
    void windowsUsesAppData() {
        assertEquals(
                Path.of("C:\\Users\\ada\\AppData\\Roaming", "jazygit", "config.toml"),
                configFile(
                        "Windows 11",
                        HashMap.of("APPDATA", "C:\\Users\\ada\\AppData\\Roaming", "XDG_CONFIG_HOME", "/xdg")));
    }

    @Test
    void windowsWithoutAppDataUsesTheRoamingFolderInTheHome() {
        assertEquals(
                Path.of("/home/ada/AppData/Roaming/jazygit/config.toml"), configFile("Windows 11", HashMap.empty()));
    }

    @Test
    void theOverrideWinsEverywhere() {
        Map<String, String> env =
                HashMap.of("JAZYGIT_CONFIG_DIR", "/mine", "XDG_CONFIG_HOME", "/xdg", "APPDATA", "C:\\AppData");

        assertEquals(Path.of("/mine/config.toml"), configFile("Linux", env));
        assertEquals(Path.of("/mine/config.toml"), configFile("Mac OS X", env));
        assertEquals(Path.of("/mine/config.toml"), configFile("Windows 11", env));
    }

    @Test
    void theStateIsInTheXdgStateHomeOnLinux() {
        assertEquals(
                Path.of("/xdg-state/jazygit/state.toml"),
                stateFile("Linux", HashMap.of("XDG_STATE_HOME", "/xdg-state", "XDG_CONFIG_HOME", "/xdg")));
    }

    @Test
    void theStateFallsBackToDotLocalStateOnLinux() {
        Path fallback = Path.of("/home/ada/.local/state/jazygit/state.toml");

        assertEquals(fallback, stateFile("Linux", HashMap.empty()));
        assertEquals(fallback, stateFile("Linux", HashMap.of("XDG_STATE_HOME", "relative/dir")));
    }

    @Test
    void theStateIsInApplicationSupportOnMacOs() {
        assertEquals(
                Path.of("/home/ada/Library/Application Support/jazygit/state.toml"),
                stateFile("Mac OS X", HashMap.empty()));
    }

    @Test
    void theStateIsInTheLocalAppDataOnWindows() {
        assertEquals(
                Path.of("C:\\Users\\ada\\AppData\\Local", "jazygit", "state.toml"),
                stateFile("Windows 11", HashMap.of("LOCALAPPDATA", "C:\\Users\\ada\\AppData\\Local")));
        assertEquals(Path.of("/home/ada/AppData/Local/jazygit/state.toml"), stateFile("Windows 11", HashMap.empty()));
    }

    @Test
    void theDebugLogIsNextToTheState() {
        assertEquals(
                Path.of("/xdg-state/jazygit/debug.log"),
                SettingsPaths.debugLog(stateFile("Linux", HashMap.of("XDG_STATE_HOME", "/xdg-state"))));
    }

    @Test
    void theStateOverrideWinsEverywhere() {
        Map<String, String> env =
                HashMap.of("JAZYGIT_STATE_DIR", "/mine", "XDG_STATE_HOME", "/xdg", "LOCALAPPDATA", "C:\\AppData");

        assertEquals(Path.of("/mine/state.toml"), stateFile("Linux", env));
        assertEquals(Path.of("/mine/state.toml"), stateFile("Mac OS X", env));
        assertEquals(Path.of("/mine/state.toml"), stateFile("Windows 11", env));
    }

    @Test
    void eachRepositoryHasADirectoryNextToTheState() {
        Path state = Path.of("/xdg-state/jazygit/state.toml");

        assertEquals(Path.of("/xdg-state/jazygit/repos"), SettingsPaths.repositories(state));
        assertEquals(
                Path.of("/xdg-state/jazygit/repos/owner/my.repo-1_x/a.toml"),
                SettingsPaths.repositoryFile(SettingsPaths.repositories(state), "owner/my.repo-1_x", "a.toml")
                        .get());
        assertEquals(Path.of("/xdg-state/jazygit/check-timings.toml"), SettingsPaths.formerCheckTimingsFile(state));
    }

    @Test
    void whatIsNoOwnerAndNameHasNoFile() {
        Path repositories = Path.of("/repos");

        for (String repository : List.of(
                "owner",
                "owner/",
                "/name",
                "owner/name/x",
                "../name",
                "owner/..",
                "owner/.",
                "owner/na me",
                "owner\\x/name",
                "")) {
            assertTrue(
                    SettingsPaths.repositoryFile(repositories, repository, "a.toml")
                            .isFailure(),
                    repository);
        }
    }
}
