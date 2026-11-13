package net.vanfleteren.jazygit.state.github;

import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * Why a pull request that was asked for is not known: the pull requests are not read yet, or could not be.
 */
public sealed interface PullRequestsError extends AppError {

    /**
     * A pull request was asked for before the pull requests were read, so it is unknown whether the branch has
     * one yet.
     */
    record NotRead() implements PullRequestsError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.pullRequest.notRead");
        }
    }

    /**
     * A pull request was asked for, but the pull requests could not be read, so it is unknown whether the branch
     * has one.
     */
    record Unavailable(Failure failure) implements PullRequestsError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.pullRequest.unavailable", Phrase.failure(failure));
        }
    }
}
