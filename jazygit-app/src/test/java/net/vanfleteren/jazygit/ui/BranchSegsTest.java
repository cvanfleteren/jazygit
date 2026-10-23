package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

class BranchSegsTest {

    @Test
    void aPullRequestShowsItsNumberItsChecksAndItsReview() {
        assertEquals(List.of(), BranchSegs.pullRequest(Option.none()));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ✅", Theme.Classes.CHECKS_PASSED),
                        LoadableList.Seg.styled(" 👍", Theme.Classes.REVIEW_APPROVED)),
                BranchSegs.pullRequest(Option.of(new PullRequest(42, "b", false, Checks.PASSING, Review.APPROVED))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #40", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ❌", Theme.Classes.CHECKS_FAILED),
                        LoadableList.Seg.styled(" ?", Theme.Classes.DIM)),
                BranchSegs.pullRequest(Option.of(new PullRequest(40, "b", false, Checks.FAILING, Review.REQUIRED))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #38", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ⏳", Theme.Classes.CHECKS_RUNNING),
                        LoadableList.Seg.styled(" 👎", Theme.Classes.REVIEW_CHANGES)),
                BranchSegs.pullRequest(
                        Option.of(new PullRequest(38, "b", false, Checks.PENDING, Review.CHANGES_REQUESTED))));
        // A draft without checks or reviews shows only its number, dimmed.
        assertEquals(
                List.of(LoadableList.Seg.styled(" #37", Theme.Classes.DIM)),
                BranchSegs.pullRequest(Option.of(new PullRequest(37, "b", true, Checks.NONE, Review.NONE))));
    }

    @Test
    void aStackedPullRequestShowsWhereItSitsInItsStack() {
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #95", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" 📚2/3", Theme.Classes.DIM),
                        LoadableList.Seg.styled(" ✅", Theme.Classes.CHECKS_PASSED)),
                BranchSegs.pullRequest(Option.of(new PullRequest(
                        95,
                        "b",
                        false,
                        Checks.PASSING,
                        Review.NONE,
                        Option.of(new PullRequestStack(96, 2, 3, List.empty()))))));
    }

    @Test
    void aMergeShowsHowItGoesInsteadOfTheChecksAndTheReview() {
        Option<PullRequest> pr = Option.of(new PullRequest(42, "b", false, Checks.FAILING, Review.APPROVED));

        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" 🔀", Theme.Classes.DIM)),
                BranchSegs.pullRequest(pr, Option.of(new MergeStatus.Pending("u-1"))));
        assertEquals(
                " 🚦",
                BranchSegs.pullRequest(pr, Option.of(new MergeStatus.Enqueued()))
                        .last()
                        .text());
        assertEquals(
                LoadableList.Seg.styled(" 💥", Theme.Classes.CHECKS_FAILED),
                BranchSegs.pullRequest(pr, Option.of(new MergeStatus.Failed("no")))
                        .last());
        assertEquals(
                " 🟣",
                BranchSegs.pullRequest(pr, Option.of(new MergeStatus.Merged()))
                        .last()
                        .text());
    }

    @Test
    void aBranchShowsItsPullRequestElseTheChecksOnItsTip() {
        PullRequest pr = new PullRequest(42, "b", false, Checks.NONE, Review.NONE);

        assertEquals(
                List.of(LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST)),
                BranchSegs.gitHub(Option.of(pr), Option.of(Checks.FAILING)));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ❌", Theme.Classes.CHECKS_FAILED)),
                BranchSegs.gitHub(Option.none(), Option.of(Checks.FAILING)));
        assertEquals(List.of(), BranchSegs.gitHub(Option.none(), Option.of(Checks.NONE)));
        assertEquals(List.of(), BranchSegs.gitHub(Option.none(), Option.none()));
    }

    @Test
    void aBranchWhoseUpstreamIsGoneSaysSo() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" gone", Theme.Classes.GONE)),
                BranchSegs.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        java.time.Instant.EPOCH,
                        false,
                        false,
                        new Tracking.Gone(new RemoteBranch("origin", new Branch.Name("b"))))));
        // A branch that was never pushed shows nothing.
        assertEquals(List.of(), BranchSegs.sync(new Branch(new Branch.Name("b"), false, new Sha("1"))));
    }

    @Test
    void aBranchCheckedOutInAnotherWorktreeSaysSo() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" (worktree)", Theme.Classes.DIM)),
                BranchSegs.worktree(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        java.time.Instant.EPOCH,
                        false,
                        false,
                        new Tracking.None(),
                        Option.of(java.nio.file.Path.of("/repos/linked")))));
        assertEquals(List.of(), BranchSegs.worktree(new Branch(new Branch.Name("b"), false, new Sha("1"))));
    }

    @Test
    void syncShowsAnAheadArrowForUnpushedAndABehindOneForIncomingCommits() {
        java.time.Instant t = java.time.Instant.EPOCH;
        assertEquals(
                List.of(),
                BranchSegs.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 0, 0)))));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD)),
                BranchSegs.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 2, 0)))));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchSegs.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 0, 1)))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD),
                        LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchSegs.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 2, 1)))));
    }

    @Test
    void aFastForwardIsShownAsAPull() {
        assertEquals(BranchSegs.busyLabel(PullCmd.PULL), BranchSegs.busyLabel(PullCmd.FAST_FORWARD));
    }
}
