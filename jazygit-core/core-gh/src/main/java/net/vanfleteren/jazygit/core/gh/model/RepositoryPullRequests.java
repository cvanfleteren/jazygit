package net.vanfleteren.jazygit.core.gh.model;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;

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
        RepoSlug repository, PullRequestList open, Option<String> viewer, Option<PrNumber> latest) {

    /**
     * Read as an account GitHub did not name, without the pull request opened last.
     */
    public RepositoryPullRequests(RepoSlug repository, PullRequestList open) {
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
                        || summary.headRepository()
                                .exists(head -> own.contains(head.value().toLowerCase(Locale.ROOT))))
                .map(PullRequestSummary::pullRequest);
    }

    /**
     * The remotes the branch of each open pull request is in, by its number, among {@code remotes}, the repository of
     * each remote, as {@code owner/name} (ADR 177): one from the repository itself is in the remotes of the
     * repository, one from a fork in those of the fork; every remote of one repository, as {@code origin} and an
     * alias of it. One whose repository is no remote's is left out, as one whose fork was deleted.
     */
    public Map<PrNumber, Set<RemoteName>> heads(Map<RemoteName, String> remotes) {
        List<Tuple2<RemoteName, String>> named =
                remotes.toList().map(remote -> Tuple.of(remote._1(), remote._2().toLowerCase(Locale.ROOT)));
        return HashMap.ofEntries(open.pullRequests()
                .flatMap(summary -> summary.headRepository()
                        .map(head -> head.value().toLowerCase(Locale.ROOT))
                        .map(head -> named.filter(remote -> remote._2().equals(head))
                                .map(Tuple2::_1)
                                .toSet())
                        .filter(in -> !in.isEmpty())
                        .map(in -> Tuple.of(summary.number(), in))));
    }

    /**
     * The open pull requests whose branch is in a fork, by their number.
     */
    public Set<PrNumber> forks() {
        return open.pullRequests()
                .filter(PullRequestSummary::fromFork)
                .map(PullRequestSummary::number)
                .toSet();
    }
}
