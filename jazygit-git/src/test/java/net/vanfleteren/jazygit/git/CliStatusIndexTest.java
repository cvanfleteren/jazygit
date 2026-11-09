package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Writing the index anew where it no longer says what the files in the working tree are like (ADR 93).
 */
class CliStatusIndexTest {

    @TempDir
    Path repo;

    @Test
    void aFileTouchedWithoutChangingItIsWrittenIntoTheIndexAgain() throws Exception {
        init();
        Files.setLastModifiedTime(
                repo.resolve("a.txt"), FileTime.from(Instant.now().plusSeconds(60)));
        // Without a refresh, git compares the stat of the file to what the index holds, and sees it differ.
        assertEquals("a.txt\n", git("--no-optional-locks", "diff-files", "--name-only"));

        assertTrue(status().refreshIndex().isRight());

        assertEquals("", git("--no-optional-locks", "diff-files", "--name-only"));
        assertEquals(0, status().read().get().files().size());
    }

    @Test
    void aConflictDoesNotKeepTheOtherFilesFromBeingWrittenIntoTheIndex() throws Exception {
        init();
        Files.writeString(repo.resolve("b.txt"), "b\n");
        git("add", "b.txt");
        git("commit", "-qm", "b");
        git("checkout", "-qb", "other");
        Files.writeString(repo.resolve("a.txt"), "other\n");
        git("commit", "-qam", "other");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("a.txt"), "main\n");
        git("commit", "-qam", "main");
        new ProcessExecutor()
                .command("git", "merge", "other")
                .directory(repo.toFile())
                .execute();
        Files.setLastModifiedTime(
                repo.resolve("b.txt"), FileTime.from(Instant.now().plusSeconds(60)));

        assertTrue(status().refreshIndex().isRight());

        // Only the conflicted file is left, once per side, as it is not merged.
        assertEquals(
                List.of("a.txt"),
                List.of(git("--no-optional-locks", "diff-files", "--name-only").split("\n"))
                        .distinct());
    }

    @Test
    void anIndexAnotherCommandHoldsIsLeftAlone() throws Exception {
        init();
        Files.writeString(repo.resolve(".git/index.lock"), "");

        assertTrue(status().refreshIndex().isLeft());
    }

    // Through the repository, as the app reaches it.
    private Status status() {
        return CliGit.open(repo).get().status();
    }

    private void init() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        git("add", ".");
        git("commit", "-qm", "init");
    }

    private String git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }
}
