package net.vanfleteren.jazygit.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parsing of {@code git status --porcelain=v2 --branch -z} output.
 */
class GitCliStatusTest {

    private static final String HASHES = "100644 100644 100644 aaaaaaa bbbbbbb";

    @Test
    void parsesBranchHeaders() {
        RepoStatus status = GitCliStatus.parse(
                "# branch.oid 0123abcd\0# branch.head main\0# branch.upstream origin/main\0");
        assertEquals("0123abcd", status.headOid());
        assertEquals("main", status.head());
        assertEquals(List.of(), status.files());
    }

    @Test
    void parsesInitialAndDetachedHeads() {
        RepoStatus status = GitCliStatus.parse("# branch.oid (initial)\0# branch.head (detached)\0");
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
                new FileEntry("added.txt", ChangeType.ADDED),
                new FileEntry("conflict.txt", ChangeType.MODIFIED),
                new FileEntry("deleted.txt", ChangeType.DELETED),
                new FileEntry("dir/untracked file.txt", ChangeType.UNTRACKED),
                new FileEntry("new name.txt", ChangeType.ADDED),
                new FileEntry("old name.txt", ChangeType.DELETED),
                new FileEntry("removed.txt", ChangeType.DELETED),
                new FileEntry("staged.txt", ChangeType.MODIFIED),
                new FileEntry("unstaged.txt", ChangeType.MODIFIED)),
                GitCliStatus.parse(raw).files());
    }

    @Test
    void branchLabelDescribesDetachedHead() {
        assertEquals("main", new RepoStatus("main", "0123456789", List.of()).branchLabel());
        assertEquals("HEAD detached at 0123456",
                new RepoStatus(RepoStatus.DETACHED, "0123456789", List.of()).branchLabel());
    }
}
