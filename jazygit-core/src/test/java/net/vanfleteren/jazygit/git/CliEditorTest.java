package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.CommitMessage;
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

    @Test
    void aMessageIsEditedInTheEditor() {
        Tuple2<CommitMessage, List<ExecutedCommand>> edited = CliEditor.editMessage(
                        repo,
                        "sh -c 'test \"$(basename \"$1\")\" = COMMIT_EDITMSG && "
                                + "printf \"%s\\n\\nbody of %s\\n\" \"$(head -n 1 \"$1\")!\" \"$(tail -n 1 \"$1\")\" > \"$1\"' -",
                        new CommitMessage("summary", "details"))
                .get();

        assertEquals(new CommitMessage("summary!", "body of details"), edited._1);
        assertEquals(1, edited._2.size());
    }

    @Test
    void anEditorThatFailsLeavesTheMessage() {
        assertEquals(
                new GitError.EditorFailed("false", 1),
                CliEditor.editMessage(repo, "false", new CommitMessage("summary", ""))
                        .getLeft()
                        .error());
    }

    @Test
    void aTextIsEditedInAFileOfTheGivenName() {
        Tuple2<String, List<ExecutedCommand>> edited = CliEditor.editText(
                        repo,
                        "sh -c 'test \"$(basename \"$1\")\" = PR_COMMENT.md && printf \"%s!\" \"$(cat \"$1\")\" > \"$1\"' -",
                        "PR_COMMENT.md",
                        "looks good")
                .get();

        assertEquals("looks good!", edited._1);
        assertEquals(1, edited._2.size());
    }

    private void configureEditor(String editor) throws Exception {
        new ProcessExecutor()
                .command("git", "-C", repo.toString(), "config", "core.editor", editor)
                .exitValueNormal()
                .execute();
    }
}
