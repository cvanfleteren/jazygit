package net.vanfleteren.jazygit.feature.inprogress;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.AppError;

/**
 * What continuing a merge, rebase, cherry-pick or revert in progress refuses up front.
 */
public sealed interface InProgressRefusal extends AppError.Refusal {

    /**
     * Continuing what the repository is in the middle of was asked for, but {@code files} files still have
     * conflicts.
     */
    record ConflictsRemain(int files) implements InProgressRefusal {
        @Override
        public String key() {
            return "error.conflictsRemain";
        }

        @Override
        public List<Object> args() {
            return List.of(files);
        }
    }
}
