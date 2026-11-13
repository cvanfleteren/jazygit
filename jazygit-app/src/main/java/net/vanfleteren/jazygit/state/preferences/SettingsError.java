package net.vanfleteren.jazygit.state.preferences;

import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What went wrong with the settings file: reading it, reading it again, or keeping a changed setting in it.
 */
public sealed interface SettingsError extends AppError {

    /**
     * What {@code problem} with the settings file says.
     */
    static Phrase said(SettingsProblem problem) {
        return switch (problem) {
            case SettingsProblem.Unreadable(var file, String reason) ->
                Phrase.of("settings.unreadable", String.valueOf(file), reason);
            case SettingsProblem.Invalid(var file, var keys) ->
                Phrase.of("settings.invalid", String.valueOf(file), keys.mkString(", "));
            case SettingsProblem.Unwritable(var file, String reason) ->
                Phrase.of("settings.unwritable", String.valueOf(file), reason);
        };
    }

    /**
     * The settings could not be read, or not all of them; the defaults are used instead.
     */
    record NotLoaded(SettingsProblem problem) implements SettingsError {

        @Override
        public Phrase phrase() {
            return said(problem);
        }
    }

    /**
     * The settings file, read again, could not be read, or has values that are not valid; the settings in use stay for
     * those (ADR 111).
     */
    record NotReread(SettingsProblem problem) implements SettingsError {

        @Override
        public Phrase phrase() {
            return switch (problem) {
                case SettingsProblem.Unreadable(var file, String reason) ->
                    Phrase.of("settings.reread.unreadable", String.valueOf(file), reason);
                case SettingsProblem.Invalid(var file, var keys) ->
                    Phrase.of("settings.reread.invalid", String.valueOf(file), keys.mkString(", "));
                case SettingsProblem.Unwritable unwritable -> said(unwritable);
            };
        }
    }

    /**
     * A changed setting could not be kept for the next session; it still holds in this one.
     */
    record NotSaved(Failure failure) implements SettingsError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.settings.notSaved", Phrase.failure(failure));
        }
    }
}
