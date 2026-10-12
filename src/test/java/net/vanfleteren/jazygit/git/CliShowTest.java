package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        List<String> lines = CliShow.changes(repo, new Sha(git("rev-parse", "HEAD").strip())).lines().collect(List.collector());

        assertEquals(List.of(
                " a.txt | 1 +",
                " b.txt | 1 -",
                " c.txt | 1 +",
                " 3 files changed, 2 insertions(+), 1 deletion(-)",
                "",
                "diff --git a/a.txt b/a.txt"), lines.take(6));
        assertTrue(lines.contains("+two"));
        assertTrue(lines.contains("-x"));
        assertTrue(lines.contains("+n"));
    }

    private String git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor().command(command).directory(repo.toFile())
                .readOutput(true).redirectErrorStream(true).exitValueNormal().execute().outputUTF8();
    }
}
