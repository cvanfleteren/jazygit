package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;

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
     * The file the settings are kept in, which the user may edit (ADR 111); none when they are kept in no file.
     */
    default Option<Path> file() {
        return Option.none();
    }

    /**
     * The file the settings are kept in, made empty, with the directories it is in, when it is not there yet, so an
     * editor can open it (ADR 111). Fails as saving does, and when they are kept in no file.
     */
    default Either<SettingsProblem, Path> made() {
        return file().toEither(() -> new SettingsProblem.Unwritable(Path.of(""), "no settings file"));
    }

    /**
     * The settings read, and what was wrong with them, if anything. Kept, too, as what the file had when it was last
     * read or written, to tell what changed in it since (ADR 111).
     */
    record Loaded(Settings settings, Option<SettingsProblem> problem) {

        public static final Loaded DEFAULT = new Loaded(Settings.DEFAULT, Option.none());

        /**
         * Whether the setting at {@code key} was read with a valid value, or without one, which is its default: not
         * when the file could not be read, nor when the value there was not valid.
         */
        boolean valid(String key) {
            return problem.forAll(found -> switch (found) {
                case SettingsProblem.Invalid invalid -> !invalid.keys().contains(key);
                case SettingsProblem.Unreadable _ -> false;
                case SettingsProblem.Unwritable _ -> true;
            });
        }

        /**
         * {@code inUse}, the settings in use, with each setting this read has a valid value of that differs from
         * what {@code before}, an earlier read, had, or that was not valid there; the others keep the value in use.
         * So a setting only the session has, as one whose save failed, stays, unless the user edited it in the file;
         * and a value that is not valid does not undo the one in use (ADR 111).
         */
        public Applied appliedTo(Settings inUse, Loaded before) {
            List<Setting<?>> changed = Setting.ALL.filter(setting -> valid(setting.key())
                    && (!before.valid(setting.key()) || setting.differs(settings, before.settings())));
            return new Applied(
                    changed.foldLeft(inUse, (applied, setting) -> setting.take(applied, settings)),
                    changed.map(Setting::key).toSet());
        }

        /**
         * What the file has once {@code next} is saved over {@code previous}: what this read, but for the settings
         * that differ between them, which saving writes, as {@code next} has them, and valid now.
         */
        public Loaded saved(Settings previous, Settings next) {
            List<Setting<?>> written = Setting.ALL.filter(setting -> setting.differs(previous, next));
            Set<String> keys = written.map(Setting::key).toSet();
            return new Loaded(
                    written.foldLeft(settings, (file, setting) -> setting.take(file, next)),
                    problem.flatMap(found -> switch (found) {
                        case SettingsProblem.Invalid(var file, var invalid) ->
                            Option.when(
                                    !invalid.removeAll(keys).isEmpty(),
                                    () -> new SettingsProblem.Invalid(file, invalid.removeAll(keys)));
                        default -> Option.of(found);
                    }));
        }
    }

    /**
     * The settings in use, once a read of the file is applied to them, and the keys of those it changed (ADR 111).
     *
     * @param settings the settings in use now
     * @param changed  the keys of the settings the read applied
     */
    record Applied(Settings settings, Set<String> changed) {

        /**
         * Whether the theme changed in the file, which replaces a theme asked for at startup, as choosing one does.
         */
        public boolean themeChanged() {
            return changed.contains(Settings.THEME);
        }
    }
}
