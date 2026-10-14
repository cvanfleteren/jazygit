package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Throwing changes away through git and the file system.
 */
class CliDiscardTest {

    @TempDir
    Path repo;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        write("a.txt", "one\n");
        write("b.txt", "one\n");
        git("add", ".");
        git("commit", "-qm", "init");
    }

    @Test
    void discardingFromHeadResetsIndexAndWorkingTree() throws Exception {
        write("a.txt", "staged\n");
        git("add", "a.txt");
        write("a.txt", "staged and more\n");

        CliIndex.checkoutFromHead(repo, List.of("a.txt"));

        assertEquals("one\n", read("a.txt"));
        assertEquals("", git("status", "--porcelain"));
    }

    @Test
    void discardingFromTheIndexKeepsWhatIsStaged() throws Exception {
        write("a.txt", "staged\n");
        git("add", "a.txt");
        write("a.txt", "staged and more\n");

        CliIndex.checkoutFromIndex(repo, List.of("a.txt"));

        assertEquals("staged\n", read("a.txt"));
        assertEquals("M  a.txt\n", git("status", "--porcelain"));
    }

    @Test
    void addedFilesAreRemovedFromIndexAndDisk() throws Exception {
        write("new.txt", "n\n");
        git("add", "new.txt");

        CliIndex.removeAdded(repo, List.of("new.txt"));

        assertFalse(Files.exists(repo.resolve("new.txt")));
        assertEquals("", git("status", "--porcelain"));
    }

    @Test
    void untrackedFilesAndDirectoriesAreDeleted() throws Exception {
        write("dir/sub/x.txt", "x\n");
        write("dir/y.txt", "y\n");
        write("z.txt", "z\n");

        assertEquals(List.of("rm -r -- dir", "rm -r -- z.txt"), CliIndex.deleteUntracked(repo, List.of("dir", "z.txt")).get().map(ExecutedCommand::line));

        assertFalse(Files.exists(repo.resolve("dir")));
        assertFalse(Files.exists(repo.resolve("z.txt")));
        assertTrue(Files.exists(repo.resolve("a.txt")));
    }

    @Test
    void pathsOutsideTheWorkingTreeAreRefused() throws Exception {
        Path outside = Files.createTempFile("outside", ".txt");
        try {
            assertInstanceOf(GitError.RefusedPath.class,
                    CliIndex.deleteUntracked(repo, List.of("../" + outside.getFileName())).getLeft().error());
            assertInstanceOf(GitError.RefusedPath.class, CliIndex.deleteUntracked(repo, List.of(".")).getLeft().error());
            assertInstanceOf(GitError.RefusedPath.class, CliIndex.deleteUntracked(repo, List.of(".git")).getLeft().error());
            assertTrue(Files.exists(outside));
            assertTrue(Files.exists(repo.resolve(".git")));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void aWholePlanCanBeAppliedInOneGo() throws Exception {
        write("a.txt", "changed\n");
        write("b.txt", "staged\n");
        git("add", "b.txt");
        write("new.txt", "n\n");
        git("add", "new.txt");
        write("loose.txt", "l\n");
        DiscardPlan plan = new DiscardPlan(List.of("loose.txt"), List.of("a.txt"), List.of("b.txt"), List.of("new.txt"));

        CliIndex.removeAdded(repo, plan.added());
        CliIndex.checkoutFromHead(repo, plan.fromHead());
        CliIndex.checkoutFromIndex(repo, plan.fromIndex());
        CliIndex.deleteUntracked(repo, plan.untracked());

        assertEquals("", git("status", "--porcelain"));
    }

    @Test
    void aPathIsTheFileOfThatNameNeverAPattern() throws Exception {
        write("*.txt", "one\n");
        git("add", "*.txt");
        git("commit", "-qm", "a file named like a pattern");
        write("*.txt", "changed\n");
        write("a.txt", "changed\n");

        CliIndex.checkoutFromHead(repo, List.of("*.txt"));

        assertEquals("one\n", read("*.txt"));
        assertEquals("changed\n", read("a.txt"));
    }

    private void write(String name, String content) throws Exception {
        Path file = repo.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String read(String name) throws Exception {
        return Files.readString(repo.resolve(name));
    }

    private String git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor().command(command).directory(repo.toFile())
                .readOutput(true).redirectErrorStream(true).exitValueNormal().execute().outputUTF8();
    }
}
