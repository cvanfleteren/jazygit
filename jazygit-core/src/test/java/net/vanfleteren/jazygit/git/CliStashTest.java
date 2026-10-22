package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

class CliStashTest {

    @TempDir
    Path repo;

    @Test
    void listsTheBranchAStashWasMadeOn() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        git("stash", "push", "-qm", "my work");

        Stash stash = CliStash.list(repo).get().head();

        assertEquals("my work", stash.message());
        assertEquals(Option.of("main"), stash.branch());
    }

    @Test
    void theChangesOfAStashAreItsStagedAndUnstagedChangesAndItsUntrackedFiles() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one\ntwo\n");
        Files.writeString(repo.resolve("staged.txt"), "s\n");
        git("add", "staged.txt");
        Files.writeString(repo.resolve("untracked.txt"), "u\n");
        git("stash", "push", "-u", "-qm", "my work");
        // What the user set for git stash show does not change what is shown.
        git("config", "stash.showPatch", "false");
        git("config", "stash.showStat", "false");
        git("config", "stash.showIncludeUntracked", "false");

        List<String> lines = CliStash.changes(repo, stash(), DiffOptions.DEFAULT)
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertEquals(
                List.of(
                        " a.txt         | 1 +",
                        " staged.txt    | 1 +",
                        " untracked.txt | 1 +",
                        " 3 files changed, 3 insertions(+)",
                        "",
                        "diff --git a/a.txt b/a.txt"),
                lines.take(6));
        assertTrue(lines.contains("+two"));
        assertTrue(lines.contains("+s"));
        assertTrue(lines.contains("+u"));
    }

    @Test
    void ignoringWhitespaceLeavesOutFilesWithChangesToWhitespaceOnly() throws Exception {
        init();
        Files.writeString(repo.resolve("a.txt"), "one \ntwo\n");
        Files.writeString(repo.resolve("b.txt"), "  x\n");
        git("stash", "push", "-q");

        List<String> lines = CliStash.changes(repo, stash(), DiffOptions.DEFAULT.withIgnoreWhitespace(true))
                .get()
                .text()
                .lines()
                .collect(List.collector());

        assertTrue(lines.contains("+two"));
        assertFalse(lines.exists(line -> line.contains("b.txt")));
    }

    private void init() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        Files.writeString(repo.resolve("b.txt"), "x\n");
        git("add", ".");
        git("commit", "-qm", "init");
    }

    private Sha stash() throws Exception {
        return new Sha(git("rev-parse", "stash@{0}").strip());
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
