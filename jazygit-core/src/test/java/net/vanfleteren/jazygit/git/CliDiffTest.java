package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

class CliDiffTest {

    @TempDir
    Path repo;

    @Test
    void showsUntrackedFilesOfARepositoryWithoutAnIndexYet() throws Exception {
        git("init", "-q");
        Files.writeString(repo.resolve("new.txt"), "first\n");

        Diffs diffs = CliDiff.diff(
                        repo, List.of(Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)), DiffOptions.DEFAULT)
                .get();

        assertTrue(diffs.unstaged().text().contains("+first"), diffs.unstaged().text());
        assertFalse(Files.exists(repo.resolve(".git/index")));
    }

    @Test
    void showsUnstagedAndUntrackedFilesWithoutTouchingTheIndex() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        git("add", "a.txt");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "two\n");
        Files.writeString(repo.resolve("new1.txt"), "first\n");
        Files.writeString(repo.resolve("new2.txt"), "second\n");
        String indexBefore = git("ls-files", "--stage");

        Diffs diffs = CliDiff.diff(
                        repo,
                        List.of(
                                Fixtures.fileEntry("a.txt", ChangeType.MODIFIED),
                                Fixtures.fileEntry("new1.txt", ChangeType.UNTRACKED),
                                Fixtures.fileEntry("new2.txt", ChangeType.UNTRACKED)),
                        DiffOptions.DEFAULT)
                .get();

        assertEquals("", diffs.staged().text());
        assertTrue(diffs.unstaged().text().contains("+two"));
        assertTrue(diffs.unstaged().text().contains("+first"));
        assertTrue(diffs.unstaged().text().contains("+second"));
        assertEquals(indexBefore, git("ls-files", "--stage"));
        assertFalse(git("status", "--porcelain").contains("A  new1.txt"));
    }

    @Test
    void ignoringWhitespaceLeavesOutChangesToWhitespaceOnly() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("spaced.txt"), "one\n");
        Files.writeString(repo.resolve("staged.txt"), "one\n");
        Files.writeString(repo.resolve("changed.txt"), "one\n");
        git("add", ".");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("spaced.txt"), "one  \n");
        Files.writeString(repo.resolve("staged.txt"), "\tone\n");
        git("add", "staged.txt");
        Files.writeString(repo.resolve("changed.txt"), "one \ntwo\n");
        Files.writeString(repo.resolve("new.txt"), "first\n");
        List<FileEntry> files = List.of(
                Fixtures.fileEntry("spaced.txt", ChangeType.MODIFIED),
                new FileEntry("staged.txt", ChangeType.MODIFIED, true, false),
                Fixtures.fileEntry("changed.txt", ChangeType.MODIFIED),
                Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED));

        Diffs shown = CliDiff.diff(repo, files, DiffOptions.DEFAULT).get();
        Diffs ignoring = CliDiff.diff(repo, files, DiffOptions.DEFAULT.withIgnoreWhitespace(true))
                .get();

        assertTrue(shown.staged().text().contains("staged.txt"), shown.staged().text());
        assertTrue(
                shown.unstaged().text().contains("spaced.txt"), shown.unstaged().text());
        assertEquals("", ignoring.staged().text());
        assertFalse(
                ignoring.unstaged().text().contains("spaced.txt"),
                ignoring.unstaged().text());
        assertTrue(
                ignoring.unstaged().text().contains("+two"), ignoring.unstaged().text());
        assertFalse(
                ignoring.unstaged().text().contains("-one"), ignoring.unstaged().text());
        assertTrue(
                ignoring.unstaged().text().contains("+first"),
                ignoring.unstaged().text());
    }

    @Test
    void theDiffsOfTheWorkingTreeKeepGitsOwnPrefixesWhateverTheUserConfigured() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        git("config", "diff.mnemonicPrefix", "true");
        git("config", "diff.srcPrefix", "old/");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "one\n");
        git("add", "a.txt", "b.txt");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "two\n");
        git("add", "a.txt");
        Files.writeString(repo.resolve("b.txt"), "two\n");
        Files.writeString(repo.resolve("new.txt"), "first\n");

        Diffs diffs = CliDiff.diff(
                        repo,
                        List.of(
                                new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                                Fixtures.fileEntry("b.txt", ChangeType.MODIFIED),
                                Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)),
                        DiffOptions.DEFAULT)
                .get();

        assertTrue(
                diffs.staged().text().contains("--- a/a.txt\n+++ b/a.txt\n"),
                diffs.staged().text());
        assertEquals(
                List.of("b.txt", "new.txt"),
                UnifiedDiff.parse(diffs.unstaged().text()).files().map(UnifiedDiff.FileDiff::path));
        assertTrue(
                diffs.unstaged().text().contains("+++ b/new.txt\n"),
                diffs.unstaged().text());
    }

    @Test
    void aLongDiffIsCutOffAndSaysSo() {
        String full = List.range(0, CliDiff.MAX_LINES).map(i -> "+" + i + "\n").mkString();

        assertEquals(DiffText.of(full), CliDiff.truncated(full));
        assertEquals(new DiffText(full, true), CliDiff.truncated(full + "+more\n"));
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
