package net.vanfleteren.jazygit.core.gh.model;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Locale;

/**
 * The open pull requests of a repository, forks included.
 *
 * @param repository the repository, as {@code owner/name}
 * @param open       its open pull requests
 * @param viewer     the login of the account they were read as, if GitHub said
 * @param latest     the number of its pull request opened last, open or not; none when it has none, or GitHub did
 *                   not say
 */
public record RepositoryPullRequests(
        String repository, PullRequestList open, Option<String> viewer, Option<PrNumber> latest) {

    /**
     * Read as an account GitHub did not name, without the pull request opened last.
     */
    public RepositoryPullRequests(String repository, PullRequestList open) {
        this(repository, open, Option.none(), Option.none());
    }

    /**
     * The open pull requests from branches of the repository itself, not from forks, as the branches show them: the
     * same read serves both (ADR 58).
     */
    public List<PullRequest> ofBranches() {
        return ofBranches(HashSet.empty());
    }

    /**
     * As {@link #ofBranches()}, with those from the forks {@code forks} too, each as {@code owner/name}: one's own
     * fork is a remote, and its pull requests into the repository are those of its branches (ADR 177). Those from
     * other forks are left out, though a branch of theirs may have the name of one of these.
     */
    public List<PullRequest> ofBranches(Set<String> forks) {
        Set<String> own = forks.map(fork -> fork.toLowerCase(Locale.ROOT));
        return open.pullRequests()
                .filter(summary -> !summary.fromFork()
                        || summary.headRepository().exists(head -> own.contains(head.toLowerCase(Locale.ROOT))))
                .map(PullRequestSummary::pullRequest);
    }
}
