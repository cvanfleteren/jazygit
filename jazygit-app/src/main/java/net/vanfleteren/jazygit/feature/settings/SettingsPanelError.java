package net.vanfleteren.jazygit.feature.settings;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What went wrong changing the settings from their panel.
 */
public sealed interface SettingsPanelError extends AppError {

    /**
     * The gh executable chosen does not run, so it is not used.
     *
     * @param executable the one chosen; none for the one on the PATH
     */
    record GhExecutableUnusable(Option<String> executable, Failure failure) implements SettingsPanelError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "error.settings.ghUnusable", Phrase.verbatim(executable.getOrElse("gh")), Phrase.failure(failure));
        }
    }

    /**
     * The account chosen for the repository could not be configured.
     */
    record GitHubAccountNotSet(Failure failure) implements SettingsPanelError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.settings.accountNotSet", Phrase.failure(failure));
        }
    }

    /**
     * The repository could not be shown again with the settings changed; they hold from the next start.
     */
    record SettingsNotApplied(Failure failure) implements SettingsPanelError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.settings.notApplied", Phrase.failure(failure));
        }
    }
}
