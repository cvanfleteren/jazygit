package net.vanfleteren.jazygit.kernel.ui.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;
import org.junit.jupiter.api.Test;

/**
 * What the user is told when an operation stopped on conflicts: what stopped, and how to go on.
 */
class NoticeTextTest {

    private static final Sha SHA = new Sha("0123456789012345678901234567890123456789");

    @Test
    void aStoppedMergeIsContinuedOrAborted() {
        assertEquals(
                "The merge stopped on 1 conflict. Resolve it, then continue or abort it (M).",
                NoticeText.of(new Notice.StoppedOnConflicts(Option.of(new InProgress.Merging(SHA)), 1)));
    }

    @Test
    void aStoppedRebaseSaysHowFarItGotAndCanBeSkipped() {
        assertEquals(
                "The rebase stopped at commit 2 of 5 on 3 conflicts. Resolve them, then continue, skip or abort it (M).",
                NoticeText.of(new Notice.StoppedOnConflicts(
                        Option.of(new InProgress.Rebasing(Option.of(new BranchName("feature")), SHA, 2, 5)), 3)));
    }

    @Test
    void aStoppedCherryPickCanBeSkipped() {
        assertEquals(
                "The cherry-pick stopped on 2 conflicts. Resolve them, then continue, skip or abort it (M).",
                NoticeText.of(new Notice.StoppedOnConflicts(Option.of(new InProgress.CherryPicking(SHA)), 2)));
    }

    @Test
    void aStoppedSquashMergeIsCommittedByTheUser() {
        assertEquals(
                "The merge stopped on 2 conflicts, and nothing was committed. Resolve them, then commit.",
                NoticeText.of(new Notice.StoppedOnConflicts(Option.none(), 2)));
    }

    @Test
    void aNoticeOfAFeatureSaysItselfAsAPhrase() {
        Notice.Phrased said = () -> Phrase.of("notice.stopped.uncommitted", Phrase.count(2));

        assertEquals(NoticeText.of(new Notice.StoppedOnConflicts(Option.none(), 2)), NoticeText.of(said));
    }

    @Test
    void aStoppedMoveSaysHowToPutTheMovedCommitsBack() {
        assertEquals(
                "The cherry-pick stopped on 1 conflict. Resolve it, then continue, skip or abort it (M)."
                        + " feature still has the moved commits: once they are on main, reset feature to 0123456^.",
                NoticeText.of(new Notice.MoveStopped(
                        new Notice.StoppedOnConflicts(Option.of(new InProgress.CherryPicking(SHA)), 1),
                        new BranchName("feature"),
                        SHA,
                        new BranchName("main"))));
    }
}
