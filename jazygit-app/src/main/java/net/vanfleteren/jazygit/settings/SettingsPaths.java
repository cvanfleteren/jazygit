package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

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
    private static final String DEBUG_LOG = "debug.log";
    private static final String CHECK_TIMINGS = "check-timings.toml";
    private static final String REPOSITORIES = "repos";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");

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

    /**
     * Where how long the checks took was kept, of the user running this, before it was kept per repository (ADR 32,
     * 63): it is only deleted.
     */
    public static Path formerCheckTimingsFile() {
        return formerCheckTimingsFile(stateFile());
    }

    /**
     * Where how long the checks took was kept before it was kept per repository, that goes with the state file
     * {@code stateFile}: next to it.
     */
    public static Path formerCheckTimingsFile(Path stateFile) {
        return stateFile.resolveSibling(CHECK_TIMINGS);
    }

    /**
     * The directory with a directory per repository, of the user running this (ADR 63).
     */
    public static Path repositories() {
        return repositories(stateFile());
    }

    /**
     * The directory with a directory per repository, of what jazygit keeps by itself of each, that goes with the state
     * file {@code stateFile}: next to it.
     */
    public static Path repositories(Path stateFile) {
        return stateFile.resolveSibling(REPOSITORIES);
    }

    /**
     * The file {@code name} of {@code repository}, as {@code owner/name}, in {@code repositories}:
     * {@code <owner>/<name>/<file>}. It fails for what is no owner and name of the letters, digits, {@code .},
     * {@code -} and {@code _} GitHub allows in them, so that no file is ever outside it.
     */
    static Try<Path> repositoryFile(Path repositories, String repository, String name) {
        List<String> parts = List.of(repository.split("/", -1));
        return parts.size() == 2
                        && parts.forAll(part -> NAME.matcher(part).matches() && !part.equals(".") && !part.equals(".."))
                ? Try.success(
                        repositories.resolve(parts.get(0)).resolve(parts.get(1)).resolve(name))
                : Try.failure(new IllegalArgumentException("No owner/name: " + repository));
    }

    /**
     * The debug log of the user running this, which {@code --debug} writes.
     */
    public static Path debugLog() {
        return debugLog(stateFile());
    }

    /**
     * The debug log that goes with the state file {@code stateFile}: next to it, as it is not edited either.
     */
    public static Path debugLog(Path stateFile) {
        return stateFile.resolveSibling(DEBUG_LOG);
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
