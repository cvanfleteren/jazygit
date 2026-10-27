package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.state.AppError;

/**
 * What stashing refuses up front.
 */
public sealed interface StashRefusal extends AppError.Refusal {

    /**
     * A stash was asked for, but nothing has changed.
     */
    record NothingToStash() implements StashRefusal {
        @Override
        public String key() {
            return "error.stash.nothing";
        }
    }
}
