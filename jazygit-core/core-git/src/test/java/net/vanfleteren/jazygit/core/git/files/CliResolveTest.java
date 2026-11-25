package net.vanfleteren.jazygit.core.git.files;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.history.CliInProgress;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Resolving a conflict by taking one side of a file, bringing it back, reading a file for its markers, and what the
 * status says is resolved (ADR 198): against a rebase of {@code feature} onto {@code main} that stopped on
 * {@code both.txt}, which both changed, and {@code gone.txt}, which main deleted and feature changed.
 */
class CliResolveTest {

    private static final Conflict BOTH = new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED);
    private static final Conflict DELETED_BY_OURS = new Conflict(Conflict.Side.DELETED, Conflict.Side.UPDATED);

    @TempDir
    Path repo;

    private CliIndex index;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        write("both.txt", "a\nb\nc\n");
        write("gone.txt", "x\n");
        git("add", ".");
        git("commit", "-qm", "base");
        git("checkout", "-qb", "feature");
        write("both.txt", "a\nFEATURE\nc\n");
        write("gone.txt", "feature\n");
        git("commit", "-qam", "feature");
        git("checkout", "-q", "main");
        write("both.txt", "a\nMAIN\nc\n");
        git("rm", "-q", "gone.txt");
        git("commit", "-qam", "main");
        git("checkout", "-q", "feature");
        gitAnyExit("rebase", "main");
        index = new CliIndex(repo);
    }

    @Test
    void keepingOursTakesHeadsVersionAndStagesIt() throws Exception {
        List<String> ran =
                index.keep("both.txt", BOTH, Conflict.Version.OURS).get().map(ExecutedCommand::line);

        // In a rebase, ours is what it rebases onto: main.
        assertEquals("a\nMAIN\nc\n", read("both.txt"));
        assertEquals(List.of("git checkout --ours -- both.txt", "git add -- both.txt"), ran);
        assertTrue(unmerged().equals("gone.txt\n"));
    }

    @Test
    void keepingTheirsTakesTheOtherVersionAndStagesIt() throws Exception {
        index.keep("both.txt", BOTH, Conflict.Version.THEIRS);

        assertEquals("a\nFEATURE\nc\n", read("both.txt"));
        assertEquals("M  both.txt\n", git("status", "--porcelain", "--", "both.txt"));
    }

    @Test
    void keepingTheSideThatDeletedAFileRemovesIt() throws Exception {
        List<String> ran = index.keep("gone.txt", DELETED_BY_OURS, Conflict.Version.OURS)
                .get()
                .map(ExecutedCommand::line);

        assertFalse(Files.exists(repo.resolve("gone.txt")));
        assertEquals(List.of("git rm --quiet -- gone.txt"), ran);
        assertEquals("both.txt\n", unmerged());
    }

    @Test
    void keepingTheSideThatChangedADeletedFileKeepsIt() throws Exception {
        index.keep("gone.txt", DELETED_BY_OURS, Conflict.Version.THEIRS);

        assertEquals("feature\n", read("gone.txt"));
        assertEquals("both.txt\n", unmerged());
    }

    @Test
    void aResolvedConflictIsBroughtBack() throws Exception {
        index.keep("both.txt", BOTH, Conflict.Version.OURS);

        List<String> ran = index.restoreConflict("both.txt").get().map(ExecutedCommand::line);

        assertEquals(List.of("git checkout --merge -- both.txt"), ran);
        assertEquals("both.txt\ngone.txt\n", unmerged());
        assertTrue(read("both.txt").contains("<<<<<<< "));
    }

    @Test
    void theMarkersOfAFileAreTheLinesTheirBlocksStartAt() throws Exception {
        write("other.txt", "no markers\n=======\nhere\n");

        assertEquals(
                List.of(new MarkedFile("both.txt", List.of(2))),
                index.markers(List.of("both.txt", "other.txt", "gone.txt", "missing.txt"))
                        .get());
    }

    @Test
    void theStatusListsTheFilesResolvedSinceItStopped() throws Exception {
        CliStatus status = new CliStatus(repo, CliInProgress.markers(repo).get(), Option.none());
        assertEquals(List.empty(), status.read().get().resolved());

        index.keep("both.txt", BOTH, Conflict.Version.THEIRS);

        assertEquals(List.of("both.txt"), status.read().get().resolved());
        assertEquals(1, status.read().get().conflicts());
    }

    @Test
    void aConflictBroughtBackIsNoLongerResolved() throws Exception {
        CliStatus status = new CliStatus(repo, CliInProgress.markers(repo).get(), Option.none());
        index.keep("both.txt", BOTH, Conflict.Version.THEIRS);
        index.keep("gone.txt", DELETED_BY_OURS, Conflict.Version.OURS);

        index.restoreConflict("both.txt");

        assertEquals(List.of("gone.txt"), status.read().get().resolved());
    }

    private String unmerged() throws Exception {
        return git("diff", "--name-only", "--diff-filter=U");
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(repo.resolve(name), content);
    }

    private String read(String name) throws Exception {
        return Files.readString(repo.resolve(name));
    }

    private void gitAnyExit(String... args) throws Exception {
        new ProcessExecutor()
                .command(List.of("git").appendAll(List.of(args)).toJavaList())
                .directory(repo.toFile())
                .readOutput(true)
                .execute();
    }

    private String git(String... args) throws Exception {
        return new ProcessExecutor()
                .command(List.of("git").appendAll(List.of(args)).toJavaList())
                .directory(repo.toFile())
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }
}
