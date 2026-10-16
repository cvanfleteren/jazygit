package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Opening files in git's editor.
 */
class CliEditorTest {

    @TempDir
    Path repo;

    @BeforeEach
    void init() throws Exception {
        new ProcessExecutor()
                .command("git", "init", "-q", repo.toString())
                .exitValueNormal()
                .execute();
    }

    @Test
    void aPlainEditorIsRunItself() {
        assertEquals(List.of("nvim", "a.txt", "b c.txt"), CliEditor.commandLine("nvim", List.of("a.txt", "b c.txt")));
    }

    @Test
    void anEditorWithArgumentsIsRunByTheShell() {
        assertEquals(
                List.of("sh", "-c", "code --wait \"$@\"", "code --wait", "a.txt"),
                CliEditor.commandLine("code --wait", List.of("a.txt")));
    }

    @Test
    void theFilesAreOpenedInTheEditor() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "original\n");

        List<ExecutedCommand> ran = CliEditor.open(repo, "sh -c 'echo edited > \"$1\"' -", List.of("a.txt"))
                .get();

        assertEquals("edited\n", Files.readString(repo.resolve("a.txt")));
        assertEquals(1, ran.size());
    }

    @Test
    void theEditorIsTheOneGitUses() throws Exception {
        // $GIT_EDITOR would win over core.editor.
        assumeTrue(System.getenv("GIT_EDITOR") == null);
        configureEditor("sh -c 'echo edited > \"$1\"' -");

        CliEditor.edit(repo, List.of("a.txt")).get();

        assertEquals("edited\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void anEditorThatExitsWithAnErrorFails() {
        assertEquals(
                new GitError.EditorFailed("false", 1),
                CliEditor.open(repo, "false", List.of("a.txt")).getLeft().error());
    }

    @Test
    void anEditorThatIsNotInstalledFails() {
        assertInstanceOf(
                GitError.EditorNotStarted.class,
                CliEditor.open(repo, "no-such-editor-for-jazygit", List.of("a.txt"))
                        .getLeft()
                        .error());
    }

    private void configureEditor(String editor) throws Exception {
        new ProcessExecutor()
                .command("git", "-C", repo.toString(), "config", "core.editor", editor)
                .exitValueNormal()
                .execute();
    }
}
