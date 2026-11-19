package net.vanfleteren.jazygit.features.changes.inprogress;

import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

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
            return Phrase.of("state.error.conflictsRemain", Phrase.count(files));
        }
    }
}
