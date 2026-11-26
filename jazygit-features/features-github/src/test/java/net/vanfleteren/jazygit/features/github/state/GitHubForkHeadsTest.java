package net.vanfleteren.jazygit.features.github.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.gh.model.RepositoryPullRequests;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.github.checks.branches.GitHubChecks;
import org.junit.jupiter.api.Test;

/**
 * A branch's pull request on GitHub is the one whose head is the branch it is pushed to (ADR 177): in the remote of
 * that branch, not one of the same name in another, as a fork's {@code main} into the project's.
 */
class GitHubForkHeadsTest {

    private static final RemoteName ORIGIN = new RemoteName("origin");
    private static final RemoteName UPSTREAM = new RemoteName("upstream");

    // Anna's pull request from her fork's main (origin) into the project's main (upstream).
    private static final PullRequest FROM_FORK = new PullRequest(
            new PrNumber(7),
            new GhBranch("main"),
            Option.of(new GhBranch("main")),
            false,
            Checks.PASSING,
            Review.NONE,
            Option.none(),
            Option.none());

    private static final GitHubState LISTED = GitHubState.NOT_READ
            .withListed(List.of(FROM_FORK))
            .withHeads(HashMap.of(FROM_FORK.number(), HashSet.of(ORIGIN)))
            .withForks(HashSet.of(FROM_FORK.number()))
            .withBranchChecks(Option.of(new GitHubChecks(HashSet.of(ORIGIN, UPSTREAM), HashMap.empty())));

    private static Branch following(String name, RemoteName remote) {
        return new Branch(
                new BranchName(name),
                false,
                new Sha("f".repeat(40)),
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                new Tracking.Tracks(
                        new Upstream(new BranchRef.Remote(new RemoteBranch(remote, new BranchName(name))), 0, 0)),
                Option.none());
    }

    @Test
    void theProjectsMainHasNotThePullRequestOfTheForksMain() {
        assertEquals(Option.none(), LISTED.pullRequest(following("main", UPSTREAM)));
        assertEquals(Option.none(), LISTED.pullRequest(new RemoteBranch(UPSTREAM, new BranchName("main"))));
    }

    @Test
    void theForksMainHasIt() {
        assertEquals(Option.of(FROM_FORK), LISTED.pullRequest(following("main", ORIGIN)));
        assertEquals(Option.of(FROM_FORK), LISTED.pullRequest(new RemoteBranch(ORIGIN, new BranchName("main"))));
    }

    @Test
    void aBranchIsNotStatedToBeBuiltOnItself() {
        assertFalse(LISTED.bases().byHead().containsKey(new BranchName("main")));
    }

    @Test
    void aPullRequestFromAForkIsKnownToBeOne() {
        assertTrue(LISTED.fromFork(FROM_FORK));
        assertFalse(GitHubState.NOT_READ.withListed(List.of(FROM_FORK)).fromFork(FROM_FORK));
    }

    @Test
    void aPullRequestOfTheRepositoryIsTheBranchsWhicheverOfItsRemotesItIsPushedTo() {
        // A plain clone with a second remote of the same repository, as an alias.
        RemoteName github = new RemoteName("github");
        PullRequestSummary same = new PullRequestSummary(
                "project/repo",
                new PrNumber(8),
                "Same repository",
                "cvf",
                new GhBranch("feature"),
                false,
                false,
                Checks.PASSING,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/project/repo/pull/8",
                Option.none(),
                Option.none(),
                Option.none(),
                Option.none(),
                Option.of("project/repo"));
        RepositoryPullRequests read = new RepositoryPullRequests("project/repo", new PullRequestList(List.of(same), 1));
        GitHubState state = GitHubState.NOT_READ
                .withListed(read.ofBranches())
                .withHeads(read.heads(HashMap.of(github, "project/repo", ORIGIN, "Project/Repo")))
                // Both remotes on GitHub, so that their remote branches are looked up.
                .withBranchChecks(Option.of(new GitHubChecks(HashSet.of(ORIGIN, github), HashMap.empty())));

        assertEquals(Option.of(same.pullRequest()), state.pullRequest(following("feature", ORIGIN)));
        assertEquals(Option.of(same.pullRequest()), state.pullRequest(following("feature", github)));
        assertEquals(
                Option.of(same.pullRequest()), state.pullRequest(new RemoteBranch(ORIGIN, new BranchName("feature"))));
    }

    @Test
    void withoutTheHeadsKnownTheNameAloneMatchesAsBefore() {
        GitHubState unknown = GitHubState.NOT_READ.withListed(List.of(FROM_FORK));

        assertEquals(Option.of(FROM_FORK), unknown.pullRequest(following("main", UPSTREAM)));
    }
}
