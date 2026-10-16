package net.vanfleteren.jazygit.settings;

import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * Where the settings file is, and the state file: each in the directory that is the standard for it on the OS.
 */
public final class SettingsPaths {

    /**
     * Names the directory of the settings file, on every OS.
     */
    static final String OVERRIDE = "JAZYGIT_CONFIG_DIR";
    /**
     * Names the directory of the state file, on every OS.
     */
    static final String STATE_OVERRIDE = "JAZYGIT_STATE_DIR";

    private static final String APP = "jazygit";
    private static final String FILE = "config.toml";
    private static final String STATE_FILE = "state.toml";

    private SettingsPaths() {}

    /**
     * The settings file of the user running this.
     */
    public static Path configFile() {
        return configFile(
                System.getProperty("os.name"),
                name -> Option.of(System.getenv(name)),
                Path.of(System.getProperty("user.home")));
    }

    /**
     * The settings file on the OS {@code osName}, given the environment variables {@code env} and the home
     * directory {@code home}.
     * <ul>
     *     <li>Linux and others: {@code $XDG_CONFIG_HOME/jazygit}, else {@code ~/.config/jazygit}</li>
     *     <li>macOS: {@code $XDG_CONFIG_HOME/jazygit}, else {@code ~/Library/Application Support/jazygit}</li>
     *     <li>Windows: {@code %APPDATA%\jazygit}</li>
     * </ul>
     * {@code $JAZYGIT_CONFIG_DIR} overrides the directory on all of them.
     */
    public static Path configFile(String osName, Function<String, Option<String>> env, Path home) {
        return set(env, OVERRIDE)
                .map(Path::of)
                .getOrElse(() ->
                        standardDir(osName.toLowerCase(Locale.ROOT), env, home).resolve(APP))
                .resolve(FILE);
    }

    /**
     * The state file of the user running this.
     */
    public static Path stateFile() {
        return stateFile(
                System.getProperty("os.name"),
                name -> Option.of(System.getenv(name)),
                Path.of(System.getProperty("user.home")));
    }

    /**
     * The state file on the OS {@code osName}, given the environment variables {@code env} and the home
     * directory {@code home}: what jazygit keeps by itself, like the repositories opened last, which the user
     * does not edit.
     * <ul>
     *     <li>Linux and others: {@code $XDG_STATE_HOME/jazygit}, else {@code ~/.local/state/jazygit}</li>
     *     <li>macOS: {@code $XDG_STATE_HOME/jazygit}, else {@code ~/Library/Application Support/jazygit}</li>
     *     <li>Windows: {@code %LOCALAPPDATA%\jazygit}</li>
     * </ul>
     * {@code $JAZYGIT_STATE_DIR} overrides the directory on all of them.
     */
    public static Path stateFile(String osName, Function<String, Option<String>> env, Path home) {
        return set(env, STATE_OVERRIDE)
                .map(Path::of)
                .getOrElse(() -> standardStateDir(osName.toLowerCase(Locale.ROOT), env, home)
                        .resolve(APP))
                .resolve(STATE_FILE);
    }

    private static Path standardStateDir(String os, Function<String, Option<String>> env, Path home) {
        if (os.startsWith("windows")) {
            return set(env, "LOCALAPPDATA")
                    .map(Path::of)
                    .getOrElse(() -> home.resolve("AppData").resolve("Local"));
        }
        Option<Path> xdg = set(env, "XDG_STATE_HOME").map(Path::of).filter(Path::isAbsolute);
        return xdg.getOrElse(() -> os.startsWith("mac") || os.startsWith("darwin")
                ? home.resolve("Library").resolve("Application Support")
                : home.resolve(".local").resolve("state"));
    }

    private static Path standardDir(String os, Function<String, Option<String>> env, Path home) {
        if (os.startsWith("windows")) {
            return set(env, "APPDATA")
                    .map(Path::of)
                    .getOrElse(() -> home.resolve("AppData").resolve("Roaming"));
        }
        // The XDG spec says a relative path is invalid and to be ignored.
        Option<Path> xdg = set(env, "XDG_CONFIG_HOME").map(Path::of).filter(Path::isAbsolute);
        return xdg.getOrElse(() -> os.startsWith("mac") || os.startsWith("darwin")
                ? home.resolve("Library").resolve("Application Support")
                : home.resolve(".config"));
    }

    private static Option<String> set(Function<String, Option<String>> env, String name) {
        return env.apply(name).filter(value -> !value.isBlank());
    }
}
