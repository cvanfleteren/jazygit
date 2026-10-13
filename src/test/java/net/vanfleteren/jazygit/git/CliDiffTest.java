package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliDiffTest {

    @TempDir
    Path repo;

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

        Diffs diffs = CliDiff.diff(repo, List.of(
                new FileEntry("a.txt", ChangeType.MODIFIED),
                new FileEntry("new1.txt", ChangeType.UNTRACKED),
                new FileEntry("new2.txt", ChangeType.UNTRACKED))).get();

        assertEquals("", diffs.staged().text());
        assertTrue(diffs.unstaged().text().contains("+two"));
        assertTrue(diffs.unstaged().text().contains("+first"));
        assertTrue(diffs.unstaged().text().contains("+second"));
        assertEquals(indexBefore, git("ls-files", "--stage"));
        assertFalse(git("status", "--porcelain").contains("A  new1.txt"));
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
        return new ProcessExecutor().command(command).directory(repo.toFile())
                .readOutput(true).redirectErrorStream(true).exitValueNormal().execute().outputUTF8();
    }
}
