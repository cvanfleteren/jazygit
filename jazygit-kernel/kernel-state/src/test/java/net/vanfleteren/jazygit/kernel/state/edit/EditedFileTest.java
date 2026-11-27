package net.vanfleteren.jazygit.kernel.state.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.core.repo.RepoPath;
import org.junit.jupiter.api.Test;

class EditedFileTest {

    @Test
    void aFileOfTheWorkingTreeIsShownByItsPathInTheRepository() {
        EditedFile file = EditedFile.of(new RepoPath("src/Main.java"));

        assertEquals("src/Main.java", file.shown());
        assertEquals("Main.java", file.name());
    }

    @Test
    void aFileElsewhereOnDiskIsShownByItsAbsolutePath() {
        EditedFile file = EditedFile.onDisk("/cache/logs/12-1.log");

        assertEquals("/cache/logs/12-1.log", file.shown());
        assertEquals("12-1.log", file.name());
    }
}
