package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
                List.of("git merge --ff --no-edit -m 'Merge branch '\\''feature'\\''' refs/heads/feature"),
                CliMerge.merge(repo, new BranchName("feature"), MergeMode.FAST_FORWARD)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(git("rev-parse", "feature"), git("rev-parse", "HEAD"));
    }

    @Test
    void fastForwardMakesAMergeCommitWhenBothBranchesMoved() throws Exception {
        commitOnMain();

        CliMerge.merge(repo, new BranchName("feature"), MergeMode.FAST_FORWARD).get();

        assertEquals(2, parents("HEAD"));
    }

    @Test
    void mergeCommitIsMadeEvenWhenAFastForwardWouldDo() throws Exception {
        assertEquals(
                List.of("git merge --no-ff --no-edit -m 'Merge branch '\\''feature'\\''' refs/heads/feature"),
                CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(2, parents("HEAD"));
        assertEquals("Merge branch 'feature'", git("log", "-1", "--format=%s").strip());
    }

    @Test
    void squashStagesTheChangesWithoutCommitting() throws Exception {
        String head = git("rev-parse", "HEAD");

        assertEquals(
                List.of("git merge --squash refs/heads/feature"),
                CliMerge.merge(repo, new BranchName("feature"), MergeMode.SQUASH)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(head, git("rev-parse", "HEAD"));
        assertEquals("A  b.txt\n", git("status", "--porcelain"));
    }

    @Test
    void squashAndCommitMakesOneCommitWithGitsSquashMessage() throws Exception {
        commitOnMain();

        assertEquals(
                List.of("git merge --squash refs/heads/feature", "git commit --quiet --no-edit"),
                CliMerge.merge(repo, new BranchName("feature"), MergeMode.SQUASH_AND_COMMIT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(1, parents("HEAD"));
        assertTrue(git("log", "-1", "--format=%B").startsWith("Squashed commit of the following:"));
        assertEquals("", git("status", "--porcelain"));
    }

    @Test
    void squashAndCommitOfAMergedBranchCommitsNothing() throws Exception {
        CliMerge.merge(repo, new BranchName("feature"), MergeMode.FAST_FORWARD).get();
        String head = git("rev-parse", "HEAD");

        assertEquals(
                List.of("git merge --squash refs/heads/feature"),
                CliMerge.merge(repo, new BranchName("feature"), MergeMode.SQUASH_AND_COMMIT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(head, git("rev-parse", "HEAD"));
    }

    @Test
    void aConflictingMergeIsLeftForTheUserToResolveAndKeepsUnrelatedChanges() throws Exception {
        conflict();
        git("checkout", "-q", "conflicting");
        write("b.txt", "conflicting\n");
        git("add", "b.txt");
        git("commit", "-qm", "conflicting changes b");
        git("checkout", "-q", "main");
        write("a.txt", "main\n");
        write("b.txt", "main\n");
        git("add", ".");
        git("commit", "-qm", "main changes a and b");
        write("unrelated.txt", "keep\n");

        GitFailure<GitError> failure = CliMerge.merge(repo, new BranchName("conflicting"), MergeMode.MERGE_COMMIT)
                .getLeft();

        assertEquals(
                new GitError.StoppedOnConflicts(
                        Option.of(new InProgress.Merging(
                                new Sha(git("rev-parse", "conflicting").strip()))),
                        2),
                failure.error());
        assertEquals(
                List.of("git merge --no-ff --no-edit -m 'Merge branch '\\''conflicting'\\''' refs/heads/conflicting"),
                failure.commands().map(ExecutedCommand::line));
        assertTrue(Files.exists(repo.resolve(".git/MERGE_HEAD")));
        assertTrue(Files.readString(repo.resolve("a.txt")).contains("<<<<<<<"));
        assertEquals("keep\n", Files.readString(repo.resolve("unrelated.txt")));
    }

    @Test
    void aConflictingSquashIsLeftUncommitted() throws Exception {
        conflict();
        write("a.txt", "main\n");
        git("commit", "-qam", "main changes a");
        String head = git("rev-parse", "HEAD");

        GitFailure<GitError> failure = CliMerge.merge(repo, new BranchName("conflicting"), MergeMode.SQUASH_AND_COMMIT)
                .getLeft();

        assertEquals(new GitError.StoppedOnConflicts(Option.none(), 1), failure.error());
        assertEquals(
                List.of("git merge --squash refs/heads/conflicting"),
                failure.commands().map(ExecutedCommand::line));
        assertTrue(git("status", "--porcelain").contains("UU a.txt"));
        assertEquals(head, git("rev-parse", "HEAD"));
    }

    @Test
    void gitRefusesToMergeOverChangesItWouldOverwrite() throws Exception {
        write("b.txt", "local\n");
        git("add", "b.txt");

        GitFailure<GitError> failure = CliMerge.merge(repo, new BranchName("feature"), MergeMode.FAST_FORWARD)
                .getLeft();

        // Nothing was started, so nothing is undone.
        assertEquals(
                List.of("git merge --ff --no-edit -m 'Merge branch '\\''feature'\\''' refs/heads/feature"),
                failure.commands().map(ExecutedCommand::line));
        assertEquals("A  b.txt\n", git("status", "--porcelain"));
    }

    @Test
    void aMergeTheUserIsResolvingIsLeftAlone() throws Exception {
        conflict();
        write("a.txt", "main\n");
        git("commit", "-qam", "main changes a");
        gitAnyExit("merge", "conflicting");
        write("a.txt", "resolved\n");

        GitFailure<GitError> failure = CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT)
                .getLeft();

        assertEquals(
                List.of("git merge --no-ff --no-edit -m 'Merge branch '\\''feature'\\''' refs/heads/feature"),
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

        GitFailure<GitError> failure = CliMerge.merge(repo, new BranchName("feature"), MergeMode.SQUASH)
                .getLeft();

        assertEquals(
                List.of("git merge --squash refs/heads/feature"),
                failure.commands().map(ExecutedCommand::line));
        assertTrue(git("status", "--porcelain").contains("UU a.txt"));
    }

    @ParameterizedTest
    @EnumSource(
            value = MergeMode.class,
            names = {"FAST_FORWARD", "MERGE_COMMIT", "SQUASH", "SQUASH_AND_COMMIT"})
    void aTagNamedLikeTheBranchDoesNotWin(MergeMode mode) throws Exception {
        // The tag is on main, so merging it would be "Already up to date".
        git("tag", "feature", "main");

        CliMerge.merge(repo, new BranchName("feature"), mode).get();

        assertTrue(Files.exists(repo.resolve("b.txt")));
    }

    @Test
    void aMergeCommitIsStillNamedAfterTheBranchNotItsRef() throws Exception {
        git("tag", "feature", "main");

        CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT).get();

        assertEquals("Merge branch 'feature'", git("log", "-1", "--format=%s").strip());
    }

    @Test
    void aMergeCommitNamesTheBranchItIsMergedIntoLikeGitDoes() throws Exception {
        git("checkout", "-qb", "develop");
        git("tag", "feature", "main");

        CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT).get();

        assertEquals(
                "Merge branch 'feature' into develop",
                git("log", "-1", "--format=%s").strip());
    }

    @Test
    void aMergeCommitIntoMasterDoesNotNameIt() throws Exception {
        git("branch", "-m", "main", "master");

        CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT).get();

        assertEquals("Merge branch 'feature'", git("log", "-1", "--format=%s").strip());
    }

    @Test
    void aMergeCommitOnADetachedHeadIsMergedIntoHeadLikeGitDoes() throws Exception {
        git("checkout", "-q", "--detach");

        CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT).get();

        assertEquals(
                "Merge branch 'feature' into HEAD",
                git("log", "-1", "--format=%s").strip());
    }

    @Test
    void aMergeCommitHonoursMergeSuppressDest() throws Exception {
        git("checkout", "-qb", "develop");
        git("config", "merge.suppressDest", "develop");

        CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT).get();

        assertEquals("Merge branch 'feature'", git("log", "-1", "--format=%s").strip());
    }

    @Test
    void aMergeCommitWithMergeLogListsTheBranchNotItsRef() throws Exception {
        git("config", "merge.log", "true");

        CliMerge.merge(repo, new BranchName("feature"), MergeMode.MERGE_COMMIT).get();

        String message = git("log", "-1", "--format=%B");
        assertTrue(message.contains("* feature:"), message);
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
