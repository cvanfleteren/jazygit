package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Notice;
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
}
