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
import net.vanfleteren.jazygit.core.repo.RemoteName;

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

    /**
     * The remote the branch of each open pull request is in, by its number, among {@code remotes}, the repository of
     * each remote, as {@code owner/name} (ADR 177): one from the repository itself is in the remote of the
     * repository, one from a fork in the remote of the fork. One whose repository is no remote's is left out, as one
     * whose fork was deleted; the first of several remotes of one repository, by name.
     */
    public Map<PrNumber, RemoteName> heads(Map<RemoteName, String> remotes) {
        List<Tuple2<RemoteName, String>> named = remotes.toList()
                .sortBy(remote -> remote._1().value())
                .map(remote -> Tuple.of(remote._1(), remote._2().toLowerCase(Locale.ROOT)));
        return HashMap.ofEntries(open.pullRequests()
                .flatMap(summary -> summary.headRepository()
                        .map(head -> head.toLowerCase(Locale.ROOT))
                        .flatMap(head -> named.find(remote -> remote._2().equals(head)))
                        .map(remote -> Tuple.of(summary.number(), remote._1()))));
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
