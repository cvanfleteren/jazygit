package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

class CliShowTest {

    @TempDir
    Path repo;

    @Test
    void listsTheFilesASummaryAndTheDiffInOneCall() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        git("add", ".");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.delete(repo.resolve("b.txt"));
        Files.writeString(repo.resolve("c.txt"), "n\n");
        git("add", "-A");
        git("commit", "-qm", "second", "-m", "body");

        List<String> lines = CliShow.changes(
                        repo, new Sha(git("rev-parse", "HEAD").strip()), DiffOptions.DEFAULT)
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertEquals(
                List.of(
                        " a.txt | 1 +",
                        " b.txt | 1 -",
                        " c.txt | 1 +",
                        " 3 files changed, 2 insertions(+), 1 deletion(-)",
                        "",
                        "diff --git a/a.txt b/a.txt"),
                lines.take(6));
        assertTrue(lines.contains("+two"));
        assertTrue(lines.contains("-x"));
        assertTrue(lines.contains("+n"));
    }

    @Test
    void ignoringWhitespaceLeavesOutFilesWithChangesToWhitespaceOnly() throws Exception {
        git("init", "-q");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        git("add", ".");
        git("commit", "-qm", "init");
        Files.writeString(repo.resolve("a.txt"), "one \ntwo\n");
        Files.writeString(repo.resolve("b.txt"), "  x\n");
        git("commit", "-qam", "second");

        List<String> lines = CliShow.changes(
                        repo, new Sha(git("rev-parse", "HEAD").strip()), DiffOptions.DEFAULT.withIgnoreWhitespace(true))
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertEquals(
                List.of(" a.txt | 1 +", " 1 file changed, 1 insertion(+)", "", "diff --git a/a.txt b/a.txt"),
                lines.take(4));
        assertTrue(lines.contains("+two"));
        assertFalse(lines.exists(line -> line.contains("b.txt")));
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
