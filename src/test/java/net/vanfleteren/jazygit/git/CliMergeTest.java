package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Merging a branch into the checked out one, in each of the four ways.
 */
class CliMergeTest {

    @TempDir
    Path repo;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        write("a.txt", "one\n");
        git("add", ".");
        git("commit", "-qm", "init");
        git("checkout", "-qb", "feature");
        write("b.txt", "feature\n");
        git("add", ".");
        git("commit", "-qm", "add b");
        git("checkout", "-q", "main");
    }

    @Test
    void fastForwardMovesTheBranchWhenItHasNothingOfItsOwn() throws Exception {
        assertEquals(
                List.of("git merge --ff --no-edit feature"),
                CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.FAST_FORWARD)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(git("rev-parse", "feature"), git("rev-parse", "HEAD"));
    }

    @Test
    void fastForwardMakesAMergeCommitWhenBothBranchesMoved() throws Exception {
        commitOnMain();

        CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.FAST_FORWARD).get();

        assertEquals(2, parents("HEAD"));
    }

    @Test
    void mergeCommitIsMadeEvenWhenAFastForwardWouldDo() throws Exception {
        assertEquals(
                List.of("git merge --no-ff --no-edit feature"),
                CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.MERGE_COMMIT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(2, parents("HEAD"));
        assertEquals("Merge branch 'feature'", git("log", "-1", "--format=%s").strip());
    }

    @Test
    void squashStagesTheChangesWithoutCommitting() throws Exception {
        String head = git("rev-parse", "HEAD");

        assertEquals(
                List.of("git merge --squash feature"),
                CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.SQUASH)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(head, git("rev-parse", "HEAD"));
        assertEquals("A  b.txt\n", git("status", "--porcelain"));
    }

    @Test
    void squashAndCommitMakesOneCommitWithGitsSquashMessage() throws Exception {
        commitOnMain();

        assertEquals(
                List.of("git merge --squash feature", "git commit --quiet --no-edit"),
                CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.SQUASH_AND_COMMIT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(1, parents("HEAD"));
        assertTrue(git("log", "-1", "--format=%B").startsWith("Squashed commit of the following:"));
        assertEquals("", git("status", "--porcelain"));
    }

    @Test
    void squashAndCommitOfAMergedBranchCommitsNothing() throws Exception {
        CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.FAST_FORWARD).get();
        String head = git("rev-parse", "HEAD");

        assertEquals(
                List.of("git merge --squash feature"),
                CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.SQUASH_AND_COMMIT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(head, git("rev-parse", "HEAD"));
    }

    @Test
    void aConflictingMergeIsAbortedAndKeepsUnrelatedChanges() throws Exception {
        conflict();
        write("a.txt", "main\n");
        git("commit", "-qam", "main changes a");
        write("unrelated.txt", "keep\n");

        GitFailure<GitError.ProcessError> failure = CliMerge.merge(
                        repo, new Branch.Name("conflicting"), MergeMode.MERGE_COMMIT)
                .getLeft();

        assertInstanceOf(GitError.CommandFailed.class, failure.error());
        assertEquals(
                List.of("git merge --no-ff --no-edit conflicting", "git merge --abort"),
                failure.commands().map(ExecutedCommand::line));
        assertFalse(Files.exists(repo.resolve(".git/MERGE_HEAD")));
        assertEquals("main\n", Files.readString(repo.resolve("a.txt")));
        assertEquals("keep\n", Files.readString(repo.resolve("unrelated.txt")));
    }

    @Test
    void aConflictingSquashIsUndone() throws Exception {
        conflict();
        write("a.txt", "main\n");
        git("commit", "-qam", "main changes a");

        GitFailure<GitError.ProcessError> failure = CliMerge.merge(
                        repo, new Branch.Name("conflicting"), MergeMode.SQUASH_AND_COMMIT)
                .getLeft();

        assertEquals(
                List.of("git merge --squash conflicting", "git reset --merge"),
                failure.commands().map(ExecutedCommand::line));
        assertEquals("", git("status", "--porcelain"));
        assertEquals("main\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void gitRefusesToMergeOverChangesItWouldOverwrite() throws Exception {
        write("b.txt", "local\n");
        git("add", "b.txt");

        GitFailure<GitError.ProcessError> failure = CliMerge.merge(
                        repo, new Branch.Name("feature"), MergeMode.FAST_FORWARD)
                .getLeft();

        // Nothing was started, so nothing is undone.
        assertEquals(
                List.of("git merge --ff --no-edit feature"), failure.commands().map(ExecutedCommand::line));
        assertEquals("A  b.txt\n", git("status", "--porcelain"));
    }

    @Test
    void aMergeTheUserIsResolvingIsLeftAlone() throws Exception {
        conflict();
        write("a.txt", "main\n");
        git("commit", "-qam", "main changes a");
        gitAnyExit("merge", "conflicting");
        write("a.txt", "resolved\n");

        GitFailure<GitError.ProcessError> failure = CliMerge.merge(
                        repo, new Branch.Name("feature"), MergeMode.MERGE_COMMIT)
                .getLeft();

        assertEquals(
                List.of("git merge --no-ff --no-edit feature"),
                failure.commands().map(ExecutedCommand::line));
        assertTrue(Files.exists(repo.resolve(".git/MERGE_HEAD")));
        assertEquals("resolved\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void aSquashOverConflictsLeftByAnotherCommandLeavesThemAlone() throws Exception {
        conflict();
        write("a.txt", "main\n");
        git("commit", "-qam", "main changes a");
        // A merge whose MERGE_HEAD is gone, as after a conflicting "git stash pop": only the conflicts remain.
        gitAnyExit("merge", "conflicting");
        Files.delete(repo.resolve(".git/MERGE_HEAD"));

        GitFailure<GitError.ProcessError> failure = CliMerge.merge(repo, new Branch.Name("feature"), MergeMode.SQUASH)
                .getLeft();

        assertEquals(List.of("git merge --squash feature"), failure.commands().map(ExecutedCommand::line));
        assertTrue(git("status", "--porcelain").contains("UU a.txt"));
    }

    private void commitOnMain() throws Exception {
        write("c.txt", "main\n");
        git("add", ".");
        git("commit", "-qm", "add c");
    }

    /**
     * A branch "conflicting" that changes a.txt.
     */
    private void conflict() throws Exception {
        git("checkout", "-qb", "conflicting");
        write("a.txt", "conflicting\n");
        git("commit", "-qam", "conflicting changes a");
        git("checkout", "-q", "main");
    }

    private int parents(String commit) throws Exception {
        return git("rev-list", "--parents", "-n", "1", commit).strip().split(" ").length - 1;
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(repo.resolve(name), content);
    }

    private void gitAnyExit(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .readOutput(true)
                .exitValueAny()
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
