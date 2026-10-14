package net.vanfleteren.jazygit.settings;

import io.vavr.control.Option;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * Where the settings file is: in the directory that is the standard for settings on the OS.
 */
public final class SettingsPaths {

    private static final String APP = "jazygit";
    private static final String FILE = "config.toml";
    /** Names the directory of the settings file, on every OS. */
    static final String OVERRIDE = "JAZYGIT_CONFIG_DIR";

    private SettingsPaths() {
    }

    /**
     * The settings file of the user running this.
     */
    public static Path configFile() {
        return configFile(System.getProperty("os.name"), name -> Option.of(System.getenv(name)),
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
        return set(env, OVERRIDE).map(Path::of)
                .getOrElse(() -> standardDir(osName.toLowerCase(Locale.ROOT), env, home).resolve(APP))
                .resolve(FILE);
    }

    private static Path standardDir(String os, Function<String, Option<String>> env, Path home) {
        if (os.startsWith("windows")) {
            return set(env, "APPDATA").map(Path::of).getOrElse(() -> home.resolve("AppData").resolve("Roaming"));
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
