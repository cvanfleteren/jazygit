package net.vanfleteren.jazygit.features.github.prs.unseen;

import io.vavr.control.Option;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.kernel.state.Env;

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
        public Option<UnseenPullRequests> load(RepoSlug repository) {
            return Option.none();
        }

        @Override
        public Try<UnseenPullRequests> save(RepoSlug repository, UnseenPullRequests unseen) {
            return Try.success(unseen);
        }
    };

    /**
     * Where the pull requests not seen yet are kept (ADR 29, 169): nowhere until one is given.
     */
    Env.Service<UnseenPullRequestsStore> SERVICE = Env.Service.of("github.unseen", NONE);

    /**
     * The unseen pull requests kept for {@code repository}, as {@code owner/name}; none when they were never kept or
     * cannot be read.
     */
    Option<UnseenPullRequests> load(RepoSlug repository);

    /**
     * Keeps {@code unseen} as the unseen pull requests of {@code repository}, as {@code owner/name}.
     */
    Try<UnseenPullRequests> save(RepoSlug repository, UnseenPullRequests unseen);
}
