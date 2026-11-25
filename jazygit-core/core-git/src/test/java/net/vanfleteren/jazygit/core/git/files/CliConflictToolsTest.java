package net.vanfleteren.jazygit.core.git.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Handing a file in conflict to git's merge tool or to mergiraf (ADR 201): against a merge of {@code feature} into
 * {@code main} that stopped on {@code imports.py}, where each added an import mergiraf can put together, and on
 * {@code value.py}, where each changed the same line, which it cannot.
 */
class CliConflictToolsTest {

    @TempDir
    Path repo;

    private CliIndex index;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        write("imports.py", "import a\n\n\ndef f():\n    return 1\n");
        write("value.py", "def f():\n    return 1\n");
        git("add", ".");
        git("commit", "-qm", "base");
        git("switch", "-qc", "feature");
        write("imports.py", "import a\nimport b\n\n\ndef f():\n    return 1\n");
        write("value.py", "def f():\n    return 3\n");
        git("commit", "-qam", "feature");
        git("switch", "-q", "main");
        write("imports.py", "import a\nimport c\n\n\ndef f():\n    return 1\n");
        write("value.py", "def f():\n    return 2\n");
        git("commit", "-qam", "main");
        gitAnyExit("merge", "feature");
        index = new CliIndex(repo);
    }

    @Test
    void theFilesInConflictAreThoseUnmerged() {
        assertEquals(
                List.of("imports.py", "value.py"),
                index.unmerged(List.of("imports.py", "value.py", "other.py")).get());
    }

    @Test
    void theToolsAreWhatTheConfigSetsAndWhetherMergirafRuns() throws Exception {
        assertEquals(
                new ConflictTools(false, false, mergirafRuns()), index.tools().get());

        git("config", "merge.tool", "mine");
        git("config", "mergetool.keepBackup", "true");

        assertEquals(
                new ConflictTools(true, true, mergirafRuns()), index.tools().get());
    }

    @Test
    void mergirafSolvesWhatItCanInPlaceAndStagesNothing() throws Exception {
        assumeTrue(mergirafRuns(), "mergiraf is not installed");
        List<String> ran = index.solve("imports.py", true).get().map(ExecutedCommand::line);

        assertEquals("import a\nimport c\nimport b\n\n\ndef f():\n    return 1\n", read("imports.py"));
        assertEquals(
                List.of("git checkout --conflict=diff3 -- imports.py", "mergiraf solve --keep-backup=false imports.py"),
                ran);
        assertEquals(
                List.of("imports.py"), index.unmerged(List.of("imports.py")).get());
        assertFalse(Files.exists(repo.resolve("imports.py.orig")));
    }

    @Test
    void conflictsMergirafLeavesAreNoFailure() throws Exception {
        assumeTrue(mergirafRuns(), "mergiraf is not installed");
        assertTrue(index.solve("value.py", true).isRight());

        assertEquals(1, index.markers(List.of("value.py")).get().size());
        assertFalse(Files.exists(repo.resolve("value.py.orig")));
    }

    @Test
    void aFileAsGitWroteItIsAsWritten() {
        assertEquals(ConflictFile.AS_WRITTEN, index.conflictFile("value.py").get());
    }

    @Test
    void aFileCheckedOutWithCrlfAsGitWroteItIsAsWritten() throws Exception {
        assertEquals(ConflictFile.AS_WRITTEN, conflictIn(repo.resolve("crlf"), "core.autocrlf", "true", "line\n"));
    }

    @Test
    void aFileThatIsNotUtf8AsGitWroteItIsAsWritten() throws Exception {
        assertEquals(
                ConflictFile.AS_WRITTEN, conflictIn(repo.resolve("latin1"), "core.autocrlf", "false", "caf\u00e9\n"));
    }

    @Test
    void aFileAddedOnBothSidesAsGitWroteItIsAsWritten() throws Exception {
        Path added = repo.resolve("added");
        Files.createDirectories(added);
        gitIn(added, "init", "-q", "-b", "main");
        gitIn(added, "config", "user.email", "t@t");
        gitIn(added, "config", "user.name", "t");
        Files.writeString(added.resolve("base.txt"), "base\n");
        gitIn(added, "add", ".");
        gitIn(added, "commit", "-qm", "base");
        gitIn(added, "switch", "-qc", "feature");
        Files.writeString(added.resolve("new.txt"), "feature\n");
        gitIn(added, "add", ".");
        gitIn(added, "commit", "-qm", "feature");
        gitIn(added, "switch", "-q", "main");
        Files.writeString(added.resolve("new.txt"), "main\n");
        gitIn(added, "add", ".");
        gitIn(added, "commit", "-qm", "main");
        gitAnyExitIn(added, "merge", "feature");

        assertEquals(
                ConflictFile.AS_WRITTEN,
                new CliIndex(added).conflictFile("new.txt").get());
    }

    @Test
    void aFileMergedByHandIsEdited() throws Exception {
        write("value.py", "def f():\n    return 23\n");

        assertEquals(ConflictFile.EDITED, index.conflictFile("value.py").get());
    }

    @Test
    void aFileWrittenWithItsBasesIsReadAsItIsThoughEdited() throws Exception {
        git("checkout", "--conflict=diff3", "--", "imports.py");
        write("imports.py", read("imports.py").replace("return 1", "return 9"));

        assertEquals(ConflictFile.WITH_BASE, index.conflictFile("imports.py").get());
    }

    @Test
    void mergirafSolvesAFileWithItsBasesAsItIsKeepingItsEdits() throws Exception {
        assumeTrue(mergirafRuns(), "mergiraf is not installed");
        git("checkout", "--conflict=diff3", "--", "imports.py");
        write("imports.py", read("imports.py").replace("return 1", "return 9"));

        List<String> ran = index.solve("imports.py", false).get().map(ExecutedCommand::line);

        assertEquals("import a\nimport c\nimport b\n\n\ndef f():\n    return 9\n", read("imports.py"));
        assertEquals(List.of("mergiraf solve --keep-backup=false imports.py"), ran);
    }

    @Test
    void theMergeToolTakesTheFileAndGitStagesItWithoutABackup() throws Exception {
        git("config", "merge.tool", "theirs");
        git("config", "mergetool.theirs.cmd", "cp \"$REMOTE\" \"$MERGED\"");
        git("config", "mergetool.theirs.trustExitCode", "true");

        List<String> ran = new CliEditor(repo)
                .mergeTool("value.py", index.tools().get())
                .get()
                .map(ExecutedCommand::line);

        assertEquals("def f():\n    return 3\n", read("value.py"));
        assertEquals(List.of("git -c mergetool.keepBackup=false mergetool -- value.py"), ran);
        assertEquals(List.empty(), index.unmerged(List.of("value.py")).get());
        assertFalse(Files.exists(repo.resolve("value.py.orig")));
    }

    @Test
    void aMergeToolThatFailsSaysSo() throws Exception {
        git("config", "merge.tool", "broken");
        git("config", "mergetool.broken.cmd", "false");
        git("config", "mergetool.broken.trustExitCode", "true");

        GitError error = new CliEditor(repo)
                .mergeTool("value.py", index.tools().get())
                .getLeft()
                .error();

        assertTrue(error instanceof GitError.ProgramFailed, error.toString());
        assertEquals(List.of("value.py"), index.unmerged(List.of("value.py")).get());
    }

    /**
     * Whether mergiraf runs here: the dev shell has it; elsewhere the tests that need it are skipped.
     */
    private static boolean mergirafRuns() {
        try {
            return new ProcessExecutor()
                            .command("mergiraf", "--version")
                            .execute()
                            .getExitValue()
                    == 0;
        } catch (Exception notThere) {
            return false;
        }
    }

    /**
     * How the file git leaves in conflict stands, in a repository at {@code at} with {@code key} set to {@code value}
     * before anything is written, whose sides each add a line to {@code content}, written as ISO-8859-1.
     */
    private ConflictFile conflictIn(Path at, String key, String value, String content) throws Exception {
        Files.createDirectories(at);
        gitIn(at, "init", "-q", "-b", "main");
        gitIn(at, "config", "user.email", "t@t");
        gitIn(at, "config", "user.name", "t");
        gitIn(at, "config", key, value);
        java.nio.charset.Charset latin1 = java.nio.charset.StandardCharsets.ISO_8859_1;
        Files.writeString(at.resolve("f.txt"), content, latin1);
        gitIn(at, "add", ".");
        gitIn(at, "commit", "-qm", "base");
        gitIn(at, "switch", "-qc", "feature");
        Files.writeString(at.resolve("f.txt"), content + "feature\n", latin1);
        gitIn(at, "commit", "-qam", "feature");
        gitIn(at, "switch", "-q", "main");
        Files.writeString(at.resolve("f.txt"), content + "main\n", latin1);
        gitIn(at, "commit", "-qam", "main");
        gitAnyExitIn(at, "merge", "feature");
        return new CliIndex(at).conflictFile("f.txt").get();
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(repo.resolve(name), content);
    }

    private String read(String name) throws Exception {
        return Files.readString(repo.resolve(name));
    }

    private void gitAnyExitIn(Path at, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        new ProcessExecutor()
                .command(command)
                .directory(at.toFile())
                .readOutput(true)
                .execute();
    }

    private void gitIn(Path at, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        new ProcessExecutor()
                .command(command)
                .directory(at.toFile())
                .readOutput(true)
                .exitValueNormal()
                .execute();
    }

    private void gitAnyExit(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .readOutput(true)
                .execute();
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
