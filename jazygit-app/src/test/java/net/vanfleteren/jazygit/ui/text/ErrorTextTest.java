package net.vanfleteren.jazygit.ui.text;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchError;
import net.vanfleteren.jazygit.feature.worktree.WorktreeError;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Phrase;
import net.vanfleteren.jazygit.state.operation.Operation;
import org.junit.jupiter.api.Test;

class ErrorTextTest {

    @Test
    void aStashThatCouldNotBeRestoredSaysTheCheckoutHappened() {
        var error = new StashNotRestored(new CommandFailed(Option.none(), 1, "conflict"));

        assertTrue(ErrorText.of(error).startsWith("Checked out, but your stashed changes"));
    }

    @Test
    void aCommitThatCameInThroughAMergeNamesTheMergeAndWhereTheBranchWouldBeLeft() {
        var error = new BranchError.MovedThroughMerge(
                new Sha("aaaaaaaaaa"), new BranchName("main"), new Sha("bbbbbbbbbb"), new Sha("cccccccccc"));

        assertEquals(
                "aaaaaaa can't be moved: it came into main through the merge bbbbbbb. It isn't one of main's own"
                        + " commits, so moving it would leave main on ccccccc, a commit of the merged branch. Pick"
                        + " bbbbbbb or a commit above it, or one on main's own line.",
                ErrorText.of(error));
    }

    // An operation whose error takes a subject and a reason: "Moving commits to {0} failed: {1}".
    private static final Operation MOVE = Operation.of("log.title.moveCommits", "error.moveCommits.failed");

    @Test
    void aCountInsideAPhraseInsideAnotherIsStillFormattedByItsChoice() {
        assertEquals(
                "Moving commits to main failed: 1 file still has conflicts",
                ErrorText.of(new AppError.Unfinished(MOVE, Option.of("main"), Option.none(), 1)));
        assertEquals(
                "Moving commits to main failed: 3 files still have conflicts",
                ErrorText.of(new AppError.Unfinished(MOVE, Option.of("main"), Option.none(), 3)));
    }

    @Test
    void aPhraseInsideAPhraseIsLookedUpAndFilledIn() {
        var rebasing = new InProgress.Rebasing(Option.none(), new Sha("aaaaaaaaaa"), 1, 2);

        assertEquals(
                "Moving commits to main failed: a rebase is in progress; continue or abort it first (M)",
                ErrorText.of(new AppError.Unfinished(MOVE, Option.of("main"), Option.of(rebasing), 0)));
    }

    @Test
    void failuresJoinedHaveTheSeparatorBetweenThem() {
        var nuked = new BranchError.NotAllNuked(
                new BranchName("feature"),
                List.of("the worktree", "the branch"),
                List.of(
                        Failure.of(new CommandFailed(Option.of("first"), 1, "")),
                        Failure.of(new CommandFailed(Option.of("second"), 1, ""))));

        assertEquals("Nuking branch feature left the worktree, the branch: first; second", ErrorText.of(nuked));
    }

    @Test
    void theWorktreeABranchIsCheckedOutInIsSaidInTheErrorOfTheOperation() {
        var elsewhere = new WorktreeError.CheckedOutElsewhere(MOVE, new BranchName("main"), "/work/other");

        assertEquals(
                "Moving commits to main failed: it is checked out in the worktree at /work/other",
                ErrorText.of(elsewhere));
    }

    @Test
    void theNumberOfAStackIsNotGrouped() {
        var refused = new BranchError.StackNotRebased(1234, "feature", BranchError.StackRefusal.CHANGES);

        assertEquals(
                "Stack #1234 is not rebased: the worktree has changes; commit or stash them first",
                ErrorText.of(refused));
    }

    @Test
    void verbatimTextIsAnArgumentNotAPattern() {
        assertEquals(
                "Moving commits to it's {0} failed: x",
                ErrorText.of(Phrase.of("error.moveCommits.failed", "it's {0}", "x")));
    }
}
