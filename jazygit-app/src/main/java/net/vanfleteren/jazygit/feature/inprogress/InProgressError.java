package net.vanfleteren.jazygit.feature.inprogress;

import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What went wrong continuing what the repository is in the middle of.
 */
public sealed interface InProgressError extends AppError {

    /**
     * Continuing what the repository is in the middle of was asked for, but {@code files} files still have
     * conflicts.
     */
    record ConflictsRemain(int files) implements InProgressError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.conflictsRemain", Phrase.count(files));
        }
    }
}
