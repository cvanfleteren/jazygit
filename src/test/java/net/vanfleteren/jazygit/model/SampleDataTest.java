package net.vanfleteren.jazygit.model;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity checks on the static placeholder data used by this stage's TUI layout.
 */
class SampleDataTest {

    private final SampleData data = new SampleData();

    @Test
    void filesAreNotEmpty() {
        List<FileEntry> files = data.status().files();
        assertFalse(files.isEmpty());
        for (FileEntry file : files) {
            assertFalse(file.path().isBlank());
        }
    }

    @Test
    void exactlyOneBranchIsCurrent() {
        List<Branch> branches = data.branches();
        long currentCount = branches.stream().filter(Branch::current).count();
        assertEquals(1, currentCount);
    }

    @Test
    void commitsHaveShortShaAndMessage() {
        List<Commit> commits = data.commits();
        assertFalse(commits.isEmpty());
        for (Commit commit : commits) {
            assertFalse(commit.shortSha().isBlank());
            assertFalse(commit.message().isBlank());
        }
    }

    @Test
    void sampleCollectionsAreConsistentAcrossCalls() {
        assertTrue(data.status().files().size() > 0);
        assertEquals(data.branches().size(), data.branches().size());
        assertEquals(data.commits().size(), data.commits().size());
    }
}
