package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * Why the pull request of a branch on Forgejo is not known (ADR 176): the pull requests are not read yet, or could
 * not be.
 */
public sealed interface PullRequestsError extends AppError {

    /**
     * It was asked for before the pull requests were read.
     */
    record NotRead() implements PullRequestsError {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.pullRequest.notRead");
        }
    }

    /**
     * It was asked for, but the pull requests could not be read.
     */
    record Unavailable(Failure failure) implements PullRequestsError {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.pullRequest.unavailable", Phrase.failure(failure));
        }
    }
}
