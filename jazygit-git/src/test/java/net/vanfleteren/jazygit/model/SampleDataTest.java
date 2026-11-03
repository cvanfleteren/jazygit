package net.vanfleteren.jazygit.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import org.junit.jupiter.api.Test;

/**
 * Sanity checks on the static placeholder data used by this stage's TUI layout.
 */
class SampleDataTest {

    private final SampleData data = new SampleData();

    @Test
    void filesAreNotEmpty() {
        List<FileEntry> files = data.status().read().get().files();
        assertFalse(files.isEmpty());
        for (FileEntry file : files) {
            assertFalse(file.path().isBlank());
        }
    }

    @Test
    void exactlyOneBranchIsCurrent() {
        List<Branch> branches = data.branches().all().get().local();
        long currentCount = branches.count(Branch::current);
        assertEquals(1, currentCount);
    }

    @Test
    void commitsHaveShortShaAndMessage() {
        List<Commit> commits = data.headLog().get();
        assertFalse(commits.isEmpty());
        for (Commit commit : commits) {
            assertFalse(commit.shortSha().isBlank());
            assertFalse(commit.message().isBlank());
        }
    }

    @Test
    void sampleCollectionsAreConsistentAcrossCalls() {
        assertTrue(data.status().read().get().files().size() > 0);
        assertEquals(
                data.branches().all().get().local().size(),
                data.branches().all().get().local().size());
        assertEquals(data.headLog().get().size(), data.headLog().get().size());
    }
}
