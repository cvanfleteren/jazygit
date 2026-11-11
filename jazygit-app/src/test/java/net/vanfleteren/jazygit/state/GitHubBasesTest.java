package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.github.GitHubChecks;
import net.vanfleteren.jazygit.state.github.GitHubState;
import net.vanfleteren.jazygit.state.github.PullRequestsUpdate;
import org.junit.jupiter.api.Test;

/**
 * The parent of a branch, from what GitHub says it is built on and the branch it was made from (ADR 72).
 */
class GitHubBasesTest {

    private static final Option<BranchName> MAIN = Option.of(name("main"));

    private static BranchName name(String name) {
        return new BranchName(name);
    }

    private static Branch branch(String name) {
        return Fixtures.branch(name(name), false, new Sha("aaaa"));
    }

    private static Branch following(String name, String onRemote) {
        return following(name, "origin", onRemote);
    }

    private static Branch following(String name, String remote, String onRemote) {
        return Fixtures.branch(
                name(name),
                false,
                new Sha("aaaa"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(
                        new BranchRef.Remote(new RemoteBranch(new RemoteName(remote), name(onRemote))), 0, 0)));
    }

    private static PullRequest pr(int number, String head, String base) {
        return new PullRequest(number, head, base, false, Checks.NONE, Review.NONE, Option.none(), Option.none());
    }

    private static PullRequest stacked(int number, String head, String base, int position, String below) {
        return stacked(number, head, base, position, below, State.OPEN);
    }

    private static PullRequest stacked(
            int number, String head, String base, int position, String below, State belowState) {
        PullRequestStack stack = new PullRequestStack(
                12,
                position,
                2,
                List.of(
                        new PullRequestStack.Entry(1, 1, belowState, below, "main"),
                        new PullRequestStack.Entry(2, number, State.OPEN, head, base)));
        return new PullRequest(number, head, base, false, Checks.NONE, Review.NONE, Option.of(stack), Option.none());
    }

    private static GitHubBases of(List<PullRequest> pullRequests, String... protectedBranches) {
        return GitHubBases.of(GitHubState.NOT_READ
                .withPullRequests(Option.of(PullRequestsUpdate.byHeadBranch(pullRequests)))
                .withProtectedBranches(Option.of(HashSet.of(protectedBranches).map(BranchName::new))));
    }

    private static GitHubBases onGitHub(List<PullRequest> pullRequests, String... remotes) {
        return GitHubBases.of(GitHubState.NOT_READ
                .withPullRequests(Option.of(PullRequestsUpdate.byHeadBranch(pullRequests)))
                .withBranchChecks(Option.of(new GitHubChecks(HashSet.of(remotes), HashMap.empty()))));
    }

    private static Map<BranchName, BranchName> parents(
            GitHubBases gitHub, List<Branch> local, Map<BranchName, BranchName> recorded) {
        return gitHub.parents(local, MAIN, recorded);
    }

    @Test
    void theBaseOfAPullRequestComesBeforeTheBranchItWasMadeFrom() {
        assertEquals(
                HashMap.of(name("feat/b"), name("feat/x")),
                parents(
                        of(List.of(pr(97, "feat/b", "feat/x"))),
                        List.of(branch("main"), branch("feat/a"), branch("feat/x"), branch("feat/b")),
                        HashMap.of(name("feat/b"), name("feat/a"))));
    }

    @Test
    void theBranchBelowInAStackComesBeforeTheBaseOfItsPullRequest() {
        assertEquals(
                HashMap.of(name("feat/b"), name("feat/a")),
                parents(
                        of(List.of(stacked(97, "feat/b", "main", 2, "feat/a"))),
                        List.of(branch("main"), branch("feat/a"), branch("feat/b")),
                        HashMap.empty()));
    }

    @Test
    void withoutAPullRequestTheBranchItWasMadeFromIsItsParent() {
        assertEquals(
                HashMap.of(name("feat/b"), name("feat/a")),
                parents(
                        GitHubBases.NONE,
                        List.of(branch("main"), branch("feat/a"), branch("feat/b")),
                        HashMap.of(name("feat/b"), name("feat/a"))));
    }

    @Test
    void aParentIsOnlyAnotherLocalBranch() {
        assertEquals(
                HashMap.empty(),
                parents(
                        GitHubBases.NONE,
                        List.of(branch("main"), branch("feat/b"), branch("feat/c")),
                        HashMap.of(name("feat/b"), name("colleague"), name("feat/c"), name("feat/c"))));
    }

    @Test
    void aPullRequestOnABranchThatIsNotCheckedOutLeavesNoParentRatherThanTheRecordedOne() {
        assertEquals(
                HashMap.empty(),
                parents(
                        of(List.of(pr(97, "feat/b", "colleague"))),
                        List.of(branch("main"), branch("feat/a"), branch("feat/b")),
                        HashMap.of(name("feat/b"), name("feat/a"))));
    }

    @Test
    void aRootHasNoParentEvenWithAPullRequest() {
        GitHubBases gitHub = of(List.of(pr(90, "develop", "main"), pr(91, "main", "develop")), "develop");

        assertEquals(
                HashMap.of(name("feat/a"), name("develop")),
                parents(
                        gitHub,
                        List.of(branch("main"), branch("develop"), branch("feat/a")),
                        HashMap.of(name("develop"), name("main"), name("feat/a"), name("develop"))));
        assertEquals(true, gitHub.root(name("develop"), MAIN));
        assertEquals(true, gitHub.root(name("main"), MAIN));
        assertEquals(false, gitHub.root(name("feat/a"), MAIN));
    }

    @Test
    void aBranchIsKnownOnGitHubByTheBranchItFollows() {
        assertEquals(
                HashMap.of(name("mine"), name("base-local")),
                parents(
                        of(List.of(pr(97, "theirs", "base-remote"))),
                        List.of(branch("main"), following("base-local", "base-remote"), following("mine", "theirs")),
                        HashMap.empty()));
    }

    @Test
    void aMergedPullRequestBelowInTheStackLeavesTheBaseGitHubMovedTheOneAboveOnto() {
        assertEquals(
                HashMap.of(name("feat/b"), name("main")),
                parents(
                        of(List.of(stacked(97, "feat/b", "main", 2, "feat/a", State.MERGED))),
                        List.of(branch("main"), branch("feat/a"), branch("feat/b")),
                        HashMap.of(name("feat/b"), name("feat/a"))));
    }

    @Test
    void aBranchOnARemoteOtherThanGitHubTakesNoPullRequestOfTheSameName() {
        assertEquals(
                HashMap.of(name("fix"), name("feat/a")),
                parents(
                        onGitHub(List.of(pr(97, "fix", "feat/x")), "origin"),
                        List.of(branch("main"), branch("feat/a"), branch("feat/x"), following("fix", "gitlab", "fix")),
                        HashMap.of(name("fix"), name("feat/a"))));
    }

    @Test
    void whileTheRemotesOnGitHubAreNotKnownABranchMayBeOnAny() {
        assertEquals(
                HashMap.of(name("fix"), name("feat/x")),
                parents(
                        of(List.of(pr(97, "fix", "feat/x"))),
                        List.of(branch("main"), branch("feat/a"), branch("feat/x"), following("fix", "gitlab", "fix")),
                        HashMap.of(name("fix"), name("feat/a"))));
    }
}
