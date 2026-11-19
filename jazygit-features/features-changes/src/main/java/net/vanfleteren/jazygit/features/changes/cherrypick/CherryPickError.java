package net.vanfleteren.jazygit.features.changes.cherrypick;

import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

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
            return Phrase.of("changes.error.cherryPick.already", sha.shortSha());
        }
    }
}
