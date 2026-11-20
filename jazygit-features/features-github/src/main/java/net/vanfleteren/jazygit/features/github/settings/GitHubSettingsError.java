package net.vanfleteren.jazygit.features.github.settings;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What went wrong changing how gh runs from the settings panel.
 */
public sealed interface GitHubSettingsError extends AppError {

    /**
     * The gh executable chosen does not run, so it is not used.
     *
     * @param executable the one chosen; none for the one on the PATH
     */
    record GhExecutableUnusable(Option<String> executable, Failure failure) implements GitHubSettingsError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "github.error.settings.ghUnusable",
                    Phrase.verbatim(executable.getOrElse("gh")),
                    Phrase.failure(failure));
        }
    }

    /**
     * The account chosen for the repository could not be configured.
     */
    record AccountNotSet(Failure failure) implements GitHubSettingsError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.settings.accountNotSet", Phrase.failure(failure));
        }
    }
}
