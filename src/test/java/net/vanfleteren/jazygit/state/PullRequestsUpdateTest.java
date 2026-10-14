package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Reading the open pull requests from GitHub.
 */
class PullRequestsUpdateTest {

    private static final PullRequest FEATURE_PR = new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);

    @Test
    void ofSeveralPullRequestsFromTheSameBranchTheNewestIsKeptWhateverTheOrder() {
        PullRequest older = new PullRequest(7, "feature", false, Checks.NONE, Review.NONE);

        assertEquals(Option.of(FEATURE_PR),
                Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(older, FEATURE_PR))).model().pullRequests().flatMap(open -> open.get("feature")));
        assertEquals(Option.of(FEATURE_PR),
                Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR, older))).model().pullRequests().flatMap(open -> open.get("feature")));
    }

    @Test
    void failingToReadThePullRequestsKeepsTheOnesShownWithoutAnError() {
        Model withPr = Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR))).model();

        Model failed = Update.update(withPr, new PullRequestsMsg.LoadFailed(refused("gh: not logged in"))).model();

        assertEquals(withPr.pullRequests(), failed.pullRequests());
        assertEquals(Option.none(), failed.error());
        assertEquals(Option.none(), failed.popup());
    }

    @Test
    void readingThePullRequestsAgainForgetsTheFailure() {
        Model failed = Update.update(loaded(), new PullRequestsMsg.LoadFailed(refused("offline"))).model();

        Model loaded = Update.update(failed, new PullRequestsMsg.Loaded(List.empty())).model();

        assertEquals(Option.none(), loaded.pullRequestsFailure());
    }

    @Test
    void aReadOfThePullRequestsIsCountedWhileItRunsAndNotStartedTwice() {
        Next reading = Update.update(loaded(), new PullRequestsMsg.Refresh());
        assertEquals(1, reading.model().readingPullRequests());

        Next again = Update.update(reading.model(), new PullRequestsMsg.Refresh());
        assertEquals(List.empty(), again.cmds());
        assertEquals(1, again.model().readingPullRequests());

        assertEquals(0, Update.update(reading.model(), new PullRequestsMsg.Loaded(List.empty())).model().readingPullRequests());
        assertEquals(0, Update.update(reading.model(), new PullRequestsMsg.LoadFailed(refused("offline"))).model()
                .readingPullRequests());
    }

    @Test
    void refreshingReadsThePullRequests() {
        assertEquals(List.of(new LoadPullRequests()), Update.update(loaded(), new PullRequestsMsg.Refresh()).cmds());
        assertInstanceOf(PullRequestsMsg.Loaded.class,
                new LoadPullRequests().run(new Env(new StubGitProvider() {
                }, StubGitHub.empty())));
    }

    @Test
    void aReadIsStartedAnywayWhenAChangeMayHaveBeenMissed() {
        Next reading = Update.update(loaded(), new PullRequestsMsg.Refresh());

        Next again = PullRequestsUpdate.loadAnyway(Next.of(reading.model()));

        assertEquals(List.of(new LoadPullRequests()), again.cmds());
        assertEquals(2, again.model().readingPullRequests());
    }
}
