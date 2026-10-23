package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * Parsing of {@code git status --porcelain=v2 --branch -z} output.
 */
class CliStatusTest {

    private static final String HASHES = "100644 100644 100644 aaaaaaa bbbbbbb";

    @Test
    void parsesWhichSideOfAConflictChangedTheFile() {
        String raw = String.join(
                "\0",
                "# branch.oid (initial)",
                "# branch.head main",
                "u UU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc both.txt",
                "u AU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc ours.txt",
                "u DU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc ours-deleted.txt",
                "u UD N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc theirs-deleted.txt",
                "u DD N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc deleted.txt",
                "");

        List<Conflict> conflicts =
                CliStatus.parse(raw).get().files().map(f -> f.conflict().get());

        assertEquals(
                List.of(
                        new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED),
                        new Conflict(Conflict.Side.DELETED, Conflict.Side.DELETED),
                        new Conflict(Conflict.Side.DELETED, Conflict.Side.UPDATED),
                        new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED),
                        new Conflict(Conflict.Side.UPDATED, Conflict.Side.DELETED)),
                conflicts);
    }

    @Test
    void parsesBranchHeaders() {
        RepoStatus status = CliStatus.parse(
                        "# branch.oid 0123abcd\0# branch.head main\0# branch.upstream origin/main\0")
                .get();
        assertEquals(Head.onBranch(new BranchName("main"), new Sha("0123abcd")), status.head());
        assertEquals(List.of(), status.files());
    }

    @Test
    void parsesInitialAndDetachedHeads() {
        assertEquals(
                new Head.OnBranch(new BranchName("main"), Option.none()),
                CliStatus.parse("# branch.oid (initial)\0# branch.head main\0")
                        .get()
                        .head());
        assertEquals(
                new Head.Detached(new Sha("0123abcd")),
                CliStatus.parse("# branch.oid 0123abcd\0# branch.head (detached)\0")
                        .get()
                        .head());
    }

    @Test
    void parsesFileEntriesSortedByPath() {
        String raw = String.join(
                "\0",
                "# branch.oid abc",
                "# branch.head main",
                "1 .M N... " + HASHES + " unstaged.txt",
                "1 M. N... " + HASHES + " staged.txt",
                "1 A. N... " + HASHES + " added.txt",
                "1 .D N... " + HASHES + " deleted.txt",
                "1 D. N... " + HASHES + " removed.txt",
                "2 R. N... " + HASHES + " R100 new name.txt",
                "old name.txt",
                "2 RM N... " + HASHES + " R90 edited.txt",
                "before edit.txt",
                "2 C. N... " + HASHES + " C100 copy.txt",
                "original.txt",
                "u UU N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc conflict.txt",
                "? dir/untracked file.txt",
                "! ignored.log",
                "");
        assertEquals(
                List.of(
                        new FileEntry("added.txt", ChangeType.ADDED, true, false),
                        FileEntry.conflicted(
                                "conflict.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED)),
                        new FileEntry("copy.txt", ChangeType.ADDED, true, false),
                        new FileEntry("deleted.txt", ChangeType.DELETED, false, true),
                        Fixtures.fileEntry("dir/untracked file.txt", ChangeType.UNTRACKED),
                        FileEntry.renamed("edited.txt", "before edit.txt", true, true),
                        FileEntry.renamed("new name.txt", "old name.txt", true, false),
                        new FileEntry("removed.txt", ChangeType.DELETED, true, false),
                        new FileEntry("staged.txt", ChangeType.MODIFIED, true, false),
                        Fixtures.fileEntry("unstaged.txt", ChangeType.MODIFIED)),
                CliStatus.parse(raw).get().files());
    }

    @Test
    void parsesStagedAndUnstagedChangesOfOneFile() {
        String raw = "# branch.head main\0" + "1 MM N... " + HASHES + " both.txt\0";
        assertEquals(
                List.of(new FileEntry("both.txt", ChangeType.MODIFIED, true, true)),
                CliStatus.parse(raw).get().files());
    }
}
