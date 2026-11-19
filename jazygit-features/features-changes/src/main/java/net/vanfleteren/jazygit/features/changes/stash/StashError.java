package net.vanfleteren.jazygit.features.changes.stash;

import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What went wrong stashing.
 */
public sealed interface StashError extends AppError {

    /**
     * A stash was asked for, but nothing has changed.
     */
    record NothingToStash() implements StashError {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.error.stash.nothing");
        }
    }
}
