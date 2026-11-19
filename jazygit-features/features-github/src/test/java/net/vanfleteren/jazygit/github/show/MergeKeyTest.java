package net.vanfleteren.jazygit.github.show;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.AutoMerge;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.MergeState;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Merging;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import org.junit.jupiter.api.Test;

/**
 * What a merge key does by what GitHub says of the pull request: the table of ADR 112, row by row.
 */
class MergeKeyTest {

    private static final AutoMerge SQUASHING = new AutoMerge(MergeMethod.SQUASH, "cvanfleteren", Option.none());

    private static final PullRequestDetails OPEN = new PullRequestDetails(
            new PrNumber(42),
            "Ready",
            "cvf",
            new PullRequestDetails.Status(PullRequestDetails.State.OPEN, false, PullRequestDetails.Mergeable.MERGEABLE),
            new PullRequestDetails.Branches(new GhBranch("feature"), new GhBranch("main")),
            new PullRequestDetails.Activity(Instant.EPOCH, Instant.EPOCH),
            new PullRequestDetails.Size(1, 1, 1, 1),
            new PullRequestDetails.Conversation(
                    Review.NONE, List.empty(), 0, PullRequestDetails.ReviewThreads.NONE, List.empty()),
            List.empty());

    private static MergeKey squash(Merging merging) {
        return MergeKey.of(OPEN.withMerging(merging), MergeMethod.SQUASH);
    }

    private static Merging state(MergeState state) {
        return new Merging(state, true, Option.none(), true, false);
    }

    @Test
    void aStackedPullRequestIsMergedNowAsGitHubHasNoAutoMergeForIt() {
        PullRequestStack stack = new PullRequestStack(
                96, 1, 1, List.of(new PullRequestStack.Entry(1, new PrNumber(42), PullRequestDetails.State.OPEN)));

        assertEquals(
                new MergeKey.Now(),
                MergeKey.of(
                        OPEN.withMerging(state(MergeState.BLOCKED)).withStack(Option.of(stack)), MergeMethod.SQUASH));
    }

    @Test
    void whileAutoMergeIsOnTheKeyOfItsMethodCancelsItWhenTheViewerCan() {
        assertEquals(
                new MergeKey.Cancel(),
                squash(new Merging(MergeState.BLOCKED, true, Option.of(SQUASHING), false, true)));
        assertEquals(
                new MergeKey.Disabled(new MergeKey.Why.AutoMergeOn(SQUASHING)),
                squash(new Merging(MergeState.BLOCKED, true, Option.of(SQUASHING), false, false)));
        assertEquals(
                new MergeKey.Disabled(new MergeKey.Why.AutoMergeOn(SQUASHING)),
                MergeKey.of(
                        OPEN.withMerging(new Merging(MergeState.CLEAN, true, Option.of(SQUASHING), false, true)),
                        MergeMethod.REBASE));
    }

    @Test
    void aPullRequestThatCanBeMergedNowIsMergedNow() {
        for (MergeState now : List.of(MergeState.CLEAN, MergeState.UNSTABLE, MergeState.HAS_HOOKS)) {
            assertEquals(new MergeKey.Now(), squash(state(now)), now.name());
        }
    }

    @Test
    void aBlockedPullRequestIsMergedWhenReadyWhenGitHubLetsIt() {
        assertEquals(new MergeKey.WhenReady(), squash(state(MergeState.BLOCKED)));
        assertEquals(
                new MergeKey.Disabled(new MergeKey.Why.NotAllowed()),
                squash(new Merging(MergeState.BLOCKED, false, Option.none(), false, false)));
        assertEquals(
                new MergeKey.Disabled(new MergeKey.Why.Blocked()),
                squash(new Merging(MergeState.BLOCKED, true, Option.none(), false, false)));
    }

    @Test
    void aPullRequestBehindItsBaseIsNotMerged() {
        assertEquals(new MergeKey.Disabled(new MergeKey.Why.Behind()), squash(state(MergeState.BEHIND)));
    }

    @Test
    void whatGitHubHasNotWorkedOutYetIsMergedNow() {
        assertEquals(new MergeKey.Now(), squash(state(MergeState.UNKNOWN)));
        assertEquals(new MergeKey.Now(), squash(Merging.UNKNOWN));
    }
}
