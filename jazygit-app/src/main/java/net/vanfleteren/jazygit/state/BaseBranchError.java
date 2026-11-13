package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * Why a branch cannot be compared with the main branch.
 */
public sealed interface BaseBranchError extends AppError {

    /**
     * There is no main branch to compare {@code branch} with: none is known, or it is gone.
     */
    record None(BranchName branch) implements BaseBranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.divergence.noBase", branch.value());
        }
    }

    /**
     * {@code branch} is the main branch.
     */
    record IsBase(BranchName branch) implements BaseBranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.divergence.isBase", branch.value());
        }
    }
}
