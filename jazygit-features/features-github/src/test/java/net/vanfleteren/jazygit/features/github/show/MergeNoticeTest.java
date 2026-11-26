package net.vanfleteren.jazygit.features.github.show;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.ui.text.NoticeText;
import org.junit.jupiter.api.Test;

/**
 * What the user is told of a merge GitHub has not done yet.
 */
class MergeNoticeTest {

    private static final BranchName FEATURE = new BranchName("feature");

    @Test
    void anEnqueuedMergeNamesItsBranch() {
        assertEquals(
                "GitHub added the pull request of feature to the merge queue.",
                NoticeText.of(new MergeNotice.Enqueued(FEATURE)));
    }

    @Test
    void aMergeStillPendingIsNoLongerCheckedOn() {
        assertEquals(
                "GitHub is still merging the pull request of feature; it is no longer checked on.",
                NoticeText.of(new MergeNotice.StillPending(FEATURE)));
    }
}
