package net.vanfleteren.jazygit.settings;

import io.vavr.control.Either;
import io.vavr.control.Option;

/**
 * Where the settings are kept between sessions.
 */
public interface SettingsStore {

    /**
     * Keeps nothing: a session starts from the defaults, and saving does nothing.
     */
    SettingsStore NONE = new SettingsStore() {
        @Override
        public Loaded load() {
            return Loaded.DEFAULT;
        }

        @Override
        public Either<SettingsProblem, Settings> save(Settings previous, Settings next) {
            return Either.right(next);
        }
    };

    /**
     * The settings kept, or the defaults for what is not kept or not valid.
     */
    Loaded load();

    /**
     * Keeps {@code next}, which the user changed from {@code previous}: only the settings that differ are
     * written, so the ones edited by hand meanwhile stay as they are.
     */
    Either<SettingsProblem, Settings> save(Settings previous, Settings next);

    /**
     * The settings read, and what was wrong with them, if anything.
     */
    record Loaded(Settings settings, Option<SettingsProblem> problem) {

        public static final Loaded DEFAULT = new Loaded(Settings.DEFAULT, Option.none());
    }
}
