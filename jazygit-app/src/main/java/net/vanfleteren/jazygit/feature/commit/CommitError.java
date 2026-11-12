package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What went wrong making or amending a commit.
 */
public sealed interface CommitError extends AppError {

    /**
     * A commit was asked for, but nothing has changed.
     */
    record NothingToCommit() implements CommitError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.commit.nothing");
        }
    }

    /**
     * Amending was asked for, but nothing has changed to amend the commit with.
     */
    record NothingToAmend() implements CommitError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.amend.nothing");
        }
    }

    /**
     * Amending a merge commit was asked for, which is not possible.
     */
    record MergeNotAmendable() implements CommitError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.amendCommit.merge");
        }
    }
}
