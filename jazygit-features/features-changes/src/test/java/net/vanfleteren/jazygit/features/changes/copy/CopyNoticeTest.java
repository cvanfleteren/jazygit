package net.vanfleteren.jazygit.features.changes.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.ui.text.NoticeText;
import org.junit.jupiter.api.Test;

/**
 * What the user is told of a copy to the clipboard (ADR 94).
 */
class CopyNoticeTest {

    @Test
    void aCopySaysHowManyLinesOrWhichPathOrSha() {
        assertEquals("Copied 1 line to the clipboard.", NoticeText.of(new CopyNotice.LinesCopied(1)));
        assertEquals("Copied 3 lines to the clipboard.", NoticeText.of(new CopyNotice.LinesCopied(3)));
        assertEquals("Copied src/A.java to the clipboard.", NoticeText.of(new CopyNotice.PathCopied("src/A.java")));
        assertEquals(
                "Copied " + "c".repeat(40) + " to the clipboard.",
                NoticeText.of(new CopyNotice.ShaCopied(new Sha("c".repeat(40)))));
    }

    @Test
    void whenNothingWasCopiedItSaysWhy() {
        assertEquals(
                "Nothing was copied: jazygit has no terminal of its own to copy with.",
                NoticeText.of(new CopyNotice.NotCopied()));
        assertEquals(
                "Nothing was copied: the terminal could not be written to.",
                NoticeText.of(new CopyNotice.CopyFailed()));
    }
}
