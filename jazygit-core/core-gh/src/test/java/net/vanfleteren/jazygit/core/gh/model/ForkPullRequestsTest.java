package net.vanfleteren.jazygit.core.gh.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import org.junit.jupiter.api.Test;

/**
 * The pull requests of one's own fork are those of its branches, as the fork is a remote; those of other forks are
 * not (ADR 177).
 */
class ForkPullRequestsTest {

    private static PullRequestSummary summary(int number, boolean fromFork, Option<String> head) {
        return new PullRequestSummary(
                new RepoSlug("project/repo"),
                new PrNumber(number),
                "Pull request " + number,
                "someone",
                new ForgeBranch("feature-" + number),
                false,
                fromFork,
                Checks.NONE,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/project/repo/pull/" + number,
                Option.none(),
                Option.none(),
                Option.none(),
                Option.none(),
                head.map(RepoSlug::new));
    }

    @Test
    void theRepositoryOfARemoteIsTheLastTwoPartsOfItsPath() {
        assertEquals(Option.of("anna/repo"), GhAccount.repositoryOf("git@github.com:anna/repo.git"));
        assertEquals(Option.of("anna/repo"), GhAccount.repositoryOf("https://github.com/anna/repo"));
        assertEquals(Option.of("anna/repo"), GhAccount.repositoryOf("ssh://git@github.com/anna/repo.git"));
        assertEquals(Option.none(), GhAccount.repositoryOf("/local/repo"));
    }

    @Test
    void thoseOfARemotesForkAreOfItsBranchesThoseOfOtherForksAreNot() {
        RepositoryPullRequests read = new RepositoryPullRequests(
                new RepoSlug("project/repo"),
                new PullRequestList(
                        List.of(
                                summary(1, false, Option.of("project/repo")),
                                summary(2, true, Option.of("Anna/Repo")),
                                summary(3, true, Option.of("stranger/repo")),
                                summary(4, true, Option.none())),
                        4));

        assertEquals(
                List.of(1, 2),
                read.ofBranches(HashSet.of("anna/repo", "project/repo"))
                        .map(pr -> pr.number().value()));
        assertEquals(List.of(1), read.ofBranches().map(pr -> pr.number().value()));
    }
}
