package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Phrase;

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
            return Phrase.of("error.stash.nothing");
        }
    }
}
