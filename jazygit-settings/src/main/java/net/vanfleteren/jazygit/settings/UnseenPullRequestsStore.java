package net.vanfleteren.jazygit.settings;

import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * Where the unseen pull requests of each repository are kept, from one session to the next. They are state that
 * jazygit keeps by itself rather than settings, as the recent repositories are: failing to read them gives none,
 * and failing to write them is not worth telling.
 */
public interface UnseenPullRequestsStore {

    /**
     * Keeps nothing: none were read before.
     */
    UnseenPullRequestsStore NONE = new UnseenPullRequestsStore() {
        @Override
        public Option<UnseenPullRequests> load(String repository) {
            return Option.none();
        }

        @Override
        public Try<UnseenPullRequests> save(String repository, UnseenPullRequests unseen) {
            return Try.success(unseen);
        }
    };

    /**
     * The unseen pull requests kept for {@code repository}, as {@code owner/name}; none when they were never kept or
     * cannot be read.
     */
    Option<UnseenPullRequests> load(String repository);

    /**
     * Keeps {@code unseen} as the unseen pull requests of {@code repository}, as {@code owner/name}.
     */
    Try<UnseenPullRequests> save(String repository, UnseenPullRequests unseen);
}
