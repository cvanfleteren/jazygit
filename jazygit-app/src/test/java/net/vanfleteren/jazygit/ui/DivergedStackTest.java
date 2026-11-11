package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.BaseDistance;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * A stack whose branches do not each have all the commits of the one below: GitHub does not merge it until it is
 * rebased, which the list says on its header and on the branch that lags (ADR 73).
 */
class DivergedStackTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEAT_C = new BranchName("feat/c");
    private static final BranchName FEAT_D = new BranchName("feat/d");

    private static final List<PullRequestStack.Entry> ENTRIES = List.of(
            new PullRequestStack.Entry(1, 98, State.OPEN, new GhBranch("feat/c"), new GhBranch("main")),
            new PullRequestStack.Entry(2, 99, State.OPEN, new GhBranch("feat/d"), new GhBranch("feat/c")));

    private static Branch branch(BranchName name) {
        return Fixtures.branch(name, false, new Sha("aaaa"));
    }

    private static PullRequest stacked(int number, String head, int position) {
        return new PullRequest(
                number,
                new GhBranch(head),
                GhBranch.UNKNOWN,
                false,
                Checks.NONE,
                Review.NONE,
                Option.of(new PullRequestStack(12, position, 2, ENTRIES)),
                Option.none());
    }

    private static BaseDistance behind(BranchName base, int commits) {
        return new BaseDistance(new BranchRef.Remote(new RemoteBranch("origin", base)), new Sha("bbbb"), 1, commits);
    }

    // main ← feat/c ← feat/d, stack #12 on GitHub; feat/d lags feat/c by dBehind, feat/c lags main by cBehind.
    private static Model model(int dBehind, int cBehind) {
        Model read = TestModels.localBranchesRead(
                        TestModels.loaded(), List.of(MAIN, FEAT_C, FEAT_D).map(DivergedStackTest::branch))
                .model();
        return read.mapRepo(repo -> repo.withParents(HashMap.of(FEAT_C, MAIN, FEAT_D, FEAT_C))
                        .withBaseDistance(HashMap.of(FEAT_C, behind(MAIN, cBehind), FEAT_D, behind(FEAT_C, dBehind))))
                .mapGitHub(gitHub -> gitHub.withPullRequests(Option.of(HashMap.of(
                        FEAT_C, stacked(98, "feat/c", 1),
                        FEAT_D, stacked(99, "feat/d", 2)))));
    }

    private static BranchNode.Header header(Model model) {
        return (BranchNode.Header) BranchNode.of(
                        List.of(branch(FEAT_C), branch(FEAT_D)),
                        model.gitHub()::pullRequest,
                        true,
                        HashSet.empty(),
                        head -> Option.none())
                .head();
    }

    @Test
    void aBranchIsBehindItsParentOnlyByTheCommitsOfThatParent() {
        assertEquals(Option.of(2), model(2, 0).repo().behindParent(FEAT_D));
        assertEquals(Option.none(), model(0, 0).repo().behindParent(FEAT_D));
        assertEquals(
                Option.none(),
                model(2, 0)
                        .mapRepo(repo -> repo.withBaseDistance(HashMap.of(FEAT_D, behind(MAIN, 2))))
                        .repo()
                        .behindParent(FEAT_D));
    }

    @Test
    void aStackWithABranchBehindTheOneBelowIsDiverged() {
        Model diverged = model(2, 0);

        assertEquals(true, LocalBranchesList.diverged(diverged, header(diverged)));
        assertEquals(Option.of(FEAT_C), LocalBranchesList.shownParent(diverged, branch(FEAT_D)));
    }

    @Test
    void aStackOnlyBehindItsBaseIsNot() {
        Model behindMain = model(0, 5);

        assertEquals(false, LocalBranchesList.diverged(behindMain, header(behindMain)));
        assertEquals(Option.none(), LocalBranchesList.shownParent(behindMain, branch(FEAT_D)));
    }
}
