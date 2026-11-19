package net.vanfleteren.jazygit.features.branches.repos;

import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What went wrong opening a recent repository.
 */
public sealed interface RecentReposError extends AppError {

    /**
     * A recent repository was chosen, but could not be opened; it is not offered anymore.
     *
     * @param directory where it was
     */
    record RepositoryNotOpened(String directory, Failure failure) implements RecentReposError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "branches.error.recentRepos.notOpened", Phrase.verbatim(directory), Phrase.failure(failure));
        }
    }
}
