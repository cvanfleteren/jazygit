package net.vanfleteren.jazygit.features.changes.files.step;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.kernel.ui.text.NoticeText;
import org.junit.jupiter.api.Test;

/**
 * What the user is told when the files pane has no file to step to (ADR 117).
 */
class FileStepNoticeTest {

    @Test
    void itSaysThereIsNoFileThatWay() {
        assertEquals("No next file: this is the last one.", NoticeText.of(new FileStepNotice.NoNextFile()));
        assertEquals("No previous file: this is the first one.", NoticeText.of(new FileStepNotice.NoPreviousFile()));
    }
}
