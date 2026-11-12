package net.vanfleteren.jazygit.feature.cherrypick;

import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What went wrong cherry-picking.
 */
public sealed interface CherryPickError extends AppError {

    /**
     * The copied commit was asked to be cherry-picked, but the checked out branch already has it.
     */
    record AlreadyPicked(Sha sha) implements CherryPickError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.cherryPick.already", sha.shortSha());
        }
    }
}
