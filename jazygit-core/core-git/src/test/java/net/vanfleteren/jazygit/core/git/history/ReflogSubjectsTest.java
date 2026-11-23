package net.vanfleteren.jazygit.core.git.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.CommitKind;
import org.junit.jupiter.api.Test;

/**
 * Reading what a reflog subject says was done, from subjects git 2.56 wrote.
 */
class ReflogSubjectsTest {

    @Test
    void aCheckoutNamesWhereHeadCameFromAndWentTo() {
        assertEquals(
                new Action.Checkout("main", "feature-x"),
                ReflogSubjects.action("checkout: moving from main to feature-x"));
        assertEquals(
                new Action.Checkout("main", "HEAD~1"), ReflogSubjects.action("checkout: moving from main to HEAD~1"));
    }

    @Test
    void aCommitSaysWhichKindItWasAndKeepsItsMessage() {
        assertEquals(new Action.Committed(CommitKind.PLAIN, "Two"), ReflogSubjects.action("commit: Two"));
        assertEquals(new Action.Committed(CommitKind.AMEND, "Two"), ReflogSubjects.action("commit (amend): Two"));
        assertEquals(new Action.Committed(CommitKind.INITIAL, "One"), ReflogSubjects.action("commit (initial): One"));
        assertEquals(
                new Action.Committed(CommitKind.MERGE, "Merge branch 'f'"),
                ReflogSubjects.action("commit (merge): Merge branch 'f'"));
        // git commit, or cherry-pick --continue, once a cherry-pick that stopped on conflicts is resolved.
        assertEquals(
                new Action.Committed(CommitKind.CHERRY_PICK, "Add c"),
                ReflogSubjects.action("commit (cherry-pick): Add c"));
        assertEquals(
                new Action.Committed(CommitKind.PLAIN, "fix: the reset: moving to x"),
                ReflogSubjects.action("commit: fix: the reset: moving to x"));
    }

    @Test
    void aResetNamesWhatItMovedTo() {
        assertEquals(new Action.Reset("HEAD~1"), ReflogSubjects.action("reset: moving to HEAD~1"));
    }

    @Test
    void aRebaseSaysItsPhase() {
        assertEquals(
                new Action.Rebase("start", "checkout main"), ReflogSubjects.action("rebase (start): checkout main"));
        assertEquals(new Action.Rebase("pick", "Add a"), ReflogSubjects.action("rebase (pick): Add a"));
        assertEquals(
                new Action.Rebase("finish", "returning to refs/heads/f"),
                ReflogSubjects.action("rebase (finish): returning to refs/heads/f"));
        assertEquals(
                new Action.Rebase("start", "checkout main"), ReflogSubjects.action("rebase -i (start): checkout main"));
        assertEquals(new Action.Rebase("abort", ""), ReflogSubjects.action("rebase (abort)"));
        assertEquals(new Action.Rebase("", "Add a"), ReflogSubjects.action("rebase: Add a"));
    }

    @Test
    void aMergeNamesWhatWasMerged() {
        assertEquals(new Action.Merge("f", "Fast-forward"), ReflogSubjects.action("merge f: Fast-forward"));
        assertEquals(
                new Action.Merge("origin/main", "Merge made by the 'ort' strategy."),
                ReflogSubjects.action("merge origin/main: Merge made by the 'ort' strategy."));
    }

    @Test
    void aPullKeepsTheStepOfItsRebase() {
        assertEquals(new Action.Pull(Option.none(), "Fast-forward"), ReflogSubjects.action("pull: Fast-forward"));
        assertEquals(
                new Action.Pull(Option.none(), "Fast-forward"),
                ReflogSubjects.action("pull -q --no-rebase: Fast-forward"));
        assertEquals(
                new Action.Pull(Option.of("start"), "checkout 1234567"),
                ReflogSubjects.action("pull --rebase (start): checkout 1234567"));
        assertEquals(
                new Action.Pull(Option.of("finish"), "returning to refs/heads/main"),
                ReflogSubjects.action("pull --rebase origin main (finish): returning to refs/heads/main"));
    }

    @Test
    void aCherryPickKeepsItsMessage() {
        assertEquals(new Action.CherryPick("Add c"), ReflogSubjects.action("cherry-pick: Add c"));
    }

    @Test
    void anythingElseIsKeptWhole() {
        assertEquals(new Action.Other("revert: Revert \"c\""), ReflogSubjects.action("revert: Revert \"c\""));
        assertEquals(new Action.Other("clone: from /tmp/x"), ReflogSubjects.action("clone: from /tmp/x"));
        assertEquals(new Action.Other(""), ReflogSubjects.action(""));
        assertEquals(new Action.Other("checkouts: a"), ReflogSubjects.action("checkouts: a"));
        assertEquals(new Action.Other("merged x: y"), ReflogSubjects.action("merged x: y"));
    }
}
