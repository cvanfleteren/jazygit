package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parsing of {@code git status --porcelain=v2 --branch -z} output.
 */
class CliStatusTest {

    private static final String HASHES = "100644 100644 100644 aaaaaaa bbbbbbb";

    @Test
    void parsesWhichSideOfAConflictChangedTheFile() {
        String raw = String.join("\0",
                "u UU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc both.txt",
                "u AU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc ours.txt",
                "u DU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc ours-deleted.txt",
                "u UD N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc theirs-deleted.txt",
                "u DD N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc deleted.txt",
                "");

        List<Conflict> conflicts = CliStatus.parse(raw).files().stream()
                .map(f -> f.conflict().orElseThrow()).toList();

        assertEquals(List.of(
                new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED),
                new Conflict(Conflict.Side.DELETED, Conflict.Side.DELETED),
                new Conflict(Conflict.Side.DELETED, Conflict.Side.UPDATED),
                new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED),
                new Conflict(Conflict.Side.UPDATED, Conflict.Side.DELETED)), conflicts);
    }

    @Test
    void parsesBranchHeaders() {
        RepoStatus status = CliStatus.parse(
                "# branch.oid 0123abcd\0# branch.head main\0# branch.upstream origin/main\0");
        assertEquals("0123abcd", status.headOid());
        assertEquals("main", status.head());
        assertEquals(List.of(), status.files());
    }

    @Test
    void parsesInitialAndDetachedHeads() {
        RepoStatus status = CliStatus.parse("# branch.oid (initial)\0# branch.head (detached)\0");
        assertEquals("(initial)", status.headOid());
        assertEquals("(detached)", status.head());
    }

    @Test
    void parsesFileEntriesSortedByPath() {
        String raw = String.join("\0",
                "# branch.oid abc",
                "# branch.head main",
                "1 .M N... " + HASHES + " unstaged.txt",
                "1 M. N... " + HASHES + " staged.txt",
                "1 A. N... " + HASHES + " added.txt",
                "1 .D N... " + HASHES + " deleted.txt",
                "1 D. N... " + HASHES + " removed.txt",
                "2 R. N... " + HASHES + " R100 new name.txt",
                "old name.txt",
                "u UU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc conflict.txt",
                "? dir/untracked file.txt",
                "! ignored.log",
                "");
        assertEquals(List.of(
                new FileEntry("added.txt", ChangeType.ADDED, true, false),
                FileEntry.conflicted("conflict.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED)),
                new FileEntry("deleted.txt", ChangeType.DELETED, false, true),
                new FileEntry("dir/untracked file.txt", ChangeType.UNTRACKED),
                new FileEntry("new name.txt", ChangeType.ADDED, true, false),
                new FileEntry("old name.txt", ChangeType.DELETED, true, false),
                new FileEntry("removed.txt", ChangeType.DELETED, true, false),
                new FileEntry("staged.txt", ChangeType.MODIFIED, true, false),
                new FileEntry("unstaged.txt", ChangeType.MODIFIED)),
                CliStatus.parse(raw).files());
    }

    @Test
    void parsesStagedAndUnstagedChangesOfOneFile() {
        String raw = "1 MM N... " + HASHES + " both.txt\0";
        assertEquals(List.of(new FileEntry("both.txt", ChangeType.MODIFIED, true, true)),
                CliStatus.parse(raw).files());
    }

    @Test
    void branchLabelDescribesDetachedHead() {
        assertEquals("main", new RepoStatus("main", "0123456789", List.of()).branchLabel());
        assertEquals("HEAD detached at 0123456",
                new RepoStatus(RepoStatus.DETACHED, "0123456789", List.of()).branchLabel());
    }
}
