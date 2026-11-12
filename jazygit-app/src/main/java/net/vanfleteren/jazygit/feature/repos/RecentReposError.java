package net.vanfleteren.jazygit.feature.repos;

import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Phrase;

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
            return Phrase.of("error.recentRepos.notOpened", Phrase.verbatim(directory), Phrase.failure(failure));
        }
    }
}
