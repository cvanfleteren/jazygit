package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What went wrong resolving a conflict.
 */
public sealed interface ResolveError extends AppError {

    /**
     * The conflict of the file at {@code path} was asked to be brought back, but none of it was resolved since the
     * operation stopped.
     */
    record NothingToRestore(RepoPath path) implements ResolveError {
        @Override
        public Phrase phrase() {
            return Phrase.of("midway.error.nothingToRestore", path.value());
        }
    }
}
