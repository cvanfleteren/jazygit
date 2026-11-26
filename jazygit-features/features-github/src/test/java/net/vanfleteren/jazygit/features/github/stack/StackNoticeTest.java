package net.vanfleteren.jazygit.features.github.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.kernel.ui.text.NoticeText;
import org.junit.jupiter.api.Test;

/**
 * What the user is told of a stack: its number is said as it is, never grouped.
 */
class StackNoticeTest {

    @Test
    void aStackNotAskedNamesItsStackWithoutGroupingTheNumber() {
        assertEquals(
                "Stack #1234 was not rebased, as another dialog was open: rebase it from the stack menu (s).",
                NoticeText.of(new StackNotice.NotAsked(1234)));
    }

    @Test
    void aStackCheckedOutCountsItsBranches() {
        assertEquals(
                "Stack #1234: 2 new, 1 fast-forwarded, 0 kept as they were.",
                NoticeText.of(new StackNotice.CheckedOut(1234, 2, 1, 0)));
    }
}
