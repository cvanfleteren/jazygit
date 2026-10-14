package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;

import java.nio.file.Path;

/**
 * Why the settings could not be read or written, as data.
 */
public sealed interface SettingsProblem {

    /**
     * The settings file this is about.
     */
    Path file();

    /**
     * The file could not be read, or is not TOML; the defaults are used instead.
     */
    record Unreadable(Path file, String reason) implements SettingsProblem {
    }

    /**
     * The file has values that are not valid for {@code keys}; the defaults are used for those.
     */
    record Invalid(Path file, List<String> keys) implements SettingsProblem {
    }

    /**
     * The file could not be written.
     */
    record Unwritable(Path file, String reason) implements SettingsProblem {
    }
}
