package net.vanfleteren.jazygit.git.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.CliGit;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.process.Limit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * All files of the working tree, and what one holds on disk (ADR 108).
 */
class CliWorkingFilesTest {

    @TempDir
    Path repo;

    @Test
    void allFilesAreTheTrackedAndTheUntrackedOnesNotIgnoredSorted() throws Exception {
        init();
        Files.writeString(repo.resolve(".gitignore"), "target/\n");
        Files.createDirectories(repo.resolve("target"));
        Files.writeString(repo.resolve("target/out.class"), "x");
        Files.writeString(repo.resolve("0-new.txt"), "new\n");
        // A tracked file deleted from disk is still listed: it is a changed file.
        Files.delete(repo.resolve("b.txt"));

        assertEquals(
                List.of(".gitignore", "0-new.txt", "a.txt", "b.txt"),
                status().allFiles().get());
    }

    @Test
    void aConflictedFileIsListedOnce() throws Exception {
        init();
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

        assertEquals(List.of("a.txt", "b.txt"), status().allFiles().get());
    }

    @Test
    void aSkipWorktreeFileIsListedOnlyWhenItIsOnDisk() throws Exception {
        init();
        git("update-index", "--skip-worktree", "a.txt", "b.txt");
        Files.delete(repo.resolve("b.txt"));

        assertEquals(List.of("a.txt"), status().allFiles().get());
    }

    @Test
    void aTextFileIsADiffOfItselfWithEveryLineAsContext() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");

        DiffText diff = text(status().content("a.txt").get());

        assertFalse(diff.truncated());
        ParsedDiff.ParsedFile file = diff.parsed().files().single();
        assertEquals("a.txt", file.file().path());
        assertEquals(
                List.of("one", "two"),
                file.hunks()
                        .flatMap(ParsedDiff.ParsedHunk::lines)
                        .map(line -> line.line().text()));
        assertTrue(file.hunks()
                .flatMap(ParsedDiff.ParsedHunk::lines)
                .forAll(line -> line.line().oldNumber().equals(line.line().newNumber())));
    }

    @Test
    void aFileWithoutANewlineAtItsEndSaysSo() {
        DiffText diff = CliWorkingFiles.asDiff("a.txt", "one".getBytes(StandardCharsets.UTF_8), new Limit(10, 100));

        assertTrue(diff.text().endsWith(" one\n\\ No newline at end of file\n"), diff.text());
    }

    @Test
    void anEmptyFileHasNoLines() {
        DiffText diff = CliWorkingFiles.asDiff("a.txt", new byte[0], new Limit(10, 100));

        assertTrue(diff.parsed().files().single().hunks().isEmpty());
    }

    @Test
    void aFileIsCutAtTheLimitOfADiff() {
        byte[] bytes = "1\n2\n3\n".getBytes(StandardCharsets.UTF_8);

        DiffText byLines = CliWorkingFiles.asDiff("a.txt", bytes, new Limit(2, 100));
        DiffText byChars = CliWorkingFiles.asDiff("a.txt", bytes, new Limit(10, 3));
        DiffText exactly = CliWorkingFiles.asDiff("a.txt", bytes, new Limit(3, 6));

        assertTrue(byLines.truncated());
        assertEquals(
                2, byLines.parsed().files().single().hunks().single().lines().size());
        assertTrue(byChars.truncated());
        assertFalse(exactly.truncated());
    }

    @Test
    void aFileWithANulByteIsBinary() throws Exception {
        init();
        Files.write(repo.resolve("image.bin"), new byte[] {'P', 'N', 'G', 0, 1, 2});

        assertEquals(new FileContent.Binary(6), status().content("image.bin").get());
    }

    @Test
    void aSymbolicLinkSaysWhereItGoes() throws Exception {
        init();
        Files.createSymbolicLink(repo.resolve("link"), Path.of("a.txt"));

        assertEquals(new FileContent.Link("a.txt"), status().content("link").get());
    }

    @Test
    void aFileThatIsGoneCannotBeRead() throws Exception {
        init();

        assertInstanceOf(GitError.Unreadable.class, status().content("gone.txt").getLeft());
    }

    private static DiffText text(FileContent content) {
        return assertInstanceOf(FileContent.Text.class, content).asDiff();
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
        Files.writeString(repo.resolve("b.txt"), "two\n");
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
