package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * The stamps of the git directory against a real repository: each one changes with what it stands for, and not
 * with what only the status shows.
 */
class CliMetadataTest {

    @TempDir
    Path dir;

    private Path main;

    private static void git(Path in, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                "git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(java.util.List.of(args));
        ProcessResult result = new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny()
                .execute();
        assertEquals(
                0, result.getExitValue(), () -> "git " + String.join(" ", args) + " failed: " + result.outputUTF8());
    }

    private static boolean gitAvailable() {
        try {
            return new ProcessExecutor("git", "--version").execute().getExitValue() == 0;
        } catch (IOException | java.util.concurrent.TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // Looked at a while after the changes, so that they tell; right after them they tell nothing.
    private static GitMetadata metadata(Path worktree) {
        return CliMetadata.read(
                CliMetadata.locate(worktree).get(),
                Instant.now().plus(CliMetadata.SETTLED).plusSeconds(1));
    }

    private static boolean reftableSupported(Path in) {
        try {
            return new ProcessExecutor("git", "init", "--quiet", "--ref-format=reftable", in.toString())
                            .execute()
                            .getExitValue()
                    == 0;
        } catch (IOException | java.util.concurrent.TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void commit(Path in, String file) throws Exception {
        Files.writeString(in.resolve(file), file);
        git(in, "add", file);
        git(in, "commit", "-m", file);
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(gitAvailable());
        main = Files.createDirectories(dir.resolve("main")).toRealPath();
        git(main, "init", "--initial-branch=main");
        commit(main, "a.txt");
    }

    @Test
    void whatOnlyTheStatusShowsChangesNoStamp() throws Exception {
        GitMetadata before = metadata(main);

        Files.writeString(main.resolve("a.txt"), "changed");
        Files.writeString(main.resolve("b.txt"), "new");
        git(main, "add", "a.txt");

        assertEquals(before, metadata(main));
    }

    @Test
    void aCommitChangesTheBranchesButNotTheStashesOrTheWorktrees() throws Exception {
        GitMetadata before = metadata(main);

        commit(main, "b.txt");

        GitMetadata after = metadata(main);
        assertNotEquals(before.branches(), after.branches());
        assertEquals(before.worktrees(), after.worktrees());
        assertEquals(before.stashes(), after.stashes());
    }

    @Test
    void theStampOfADiffChangesWithItsFilesAndTheIndexOnly() throws Exception {
        Files.writeString(main.resolve("a.txt"), "changed");
        Files.writeString(main.resolve("b.txt"), "other");
        List<FileEntry> files = List.of(new FileEntry("a.txt", ChangeType.MODIFIED, false, true));
        Option<MetadataStamp> before = diffStamp(files);

        Files.writeString(main.resolve("b.txt"), "other, edited");
        Option<MetadataStamp> otherEdited = diffStamp(files);
        Files.writeString(main.resolve("a.txt"), "changed again");
        Option<MetadataStamp> edited = diffStamp(files);
        git(main, "add", "a.txt");

        assertTrue(before.isDefined());
        assertEquals(before, otherEdited);
        assertNotEquals(otherEdited, edited);
        assertNotEquals(edited, diffStamp(files));
    }

    private Option<MetadataStamp> diffStamp(List<FileEntry> files) {
        return CliMetadata.diff(
                CliMetadata.locate(main).get(),
                main,
                files,
                Instant.now().plus(CliMetadata.SETTLED).plusSeconds(1));
    }

    @Test
    void aFetchThatBringsNothingChangesNoStamp() throws Exception {
        Path origin = dir.resolve("origin.git");
        git(dir, "init", "--bare", "--initial-branch=main", origin.toString());
        git(main, "remote", "add", "origin", origin.toString());
        git(main, "push", "-u", "origin", "main");
        git(main, "fetch", "origin");
        GitMetadata before = metadata(main);

        git(main, "fetch", "--prune", "--no-write-fetch-head", "origin");

        assertEquals(before, metadata(main));
    }

    @Test
    void aRemoteTrackingBranchThatComesOrGoesChangesTheBranches() throws Exception {
        git(main, "update-ref", "refs/remotes/origin/main", "HEAD");
        GitMetadata before = metadata(main);

        git(main, "update-ref", "refs/remotes/origin/feature/x", "HEAD");
        GitMetadata added = metadata(main);
        git(main, "update-ref", "-d", "refs/remotes/origin/feature/x");

        assertNotEquals(before.branches(), added.branches());
        assertNotEquals(added.branches(), metadata(main).branches());
    }

    @Test
    void checkingOutAnotherBranchOrACommitChangesTheWorktrees() throws Exception {
        git(main, "branch", "other");
        GitMetadata before = metadata(main);

        git(main, "checkout", "other");
        GitMetadata switched = metadata(main);
        git(main, "checkout", "--detach");

        assertNotEquals(before.worktrees(), switched.worktrees());
        assertNotEquals(switched.worktrees(), metadata(main).worktrees());
    }

    @Test
    void aParentThatIsRecordedChangesTheBranches() throws Exception {
        git(main, "branch", "feature");
        GitMetadata before = metadata(main);

        git(main, "config", "branch.feature.gh-merge-base", "main");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void checkingOutInAReftableRepositoryChangesTheWorktrees() throws Exception {
        Path reftable = dir.resolve("reftable");
        assumeTrue(reftableSupported(reftable));
        commit(reftable, "a.txt");
        git(reftable, "branch", "other");
        GitMetadata before = metadata(reftable);

        git(reftable, "checkout", "other");

        assertNotEquals(before.worktrees(), metadata(reftable).worktrees());
    }

    @Test
    void checkingOutInALinkedWorktreeOfAReftableRepositoryChangesTheWorktreesAndTheBranches() throws Exception {
        Path reftable = dir.resolve("reftable");
        assumeTrue(reftableSupported(reftable));
        commit(reftable, "a.txt");
        git(reftable, "branch", "other");
        Path linked = dir.resolve("linked");
        git(reftable, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata before = metadata(reftable);

        git(linked, "checkout", "other");

        GitMetadata after = metadata(reftable);
        assertNotEquals(before.worktrees(), after.worktrees());
        assertNotEquals(before.branches(), after.branches());
    }

    @Test
    void movingABranchInADirectoryOfRefsChangesTheBranches() throws Exception {
        commit(main, "b.txt");
        git(main, "branch", "feature/deep/x");
        GitMetadata before = metadata(main);

        git(main, "branch", "--force", "feature/deep/x", "HEAD~1");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void anUpstreamThatIsSetChangesTheBranches() throws Exception {
        git(main, "branch", "feature");
        GitMetadata before = metadata(main);

        git(main, "branch", "--set-upstream-to=main", "feature");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void packingTheRefsChangesTheBranches() throws Exception {
        git(main, "branch", "feature");
        GitMetadata before = metadata(main);

        git(main, "pack-refs", "--all");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void pushingAndDroppingAStashChangesTheStashes() throws Exception {
        GitMetadata before = metadata(main);

        Files.writeString(main.resolve("a.txt"), "changed");
        git(main, "stash", "push");
        GitMetadata pushed = metadata(main);
        Files.writeString(main.resolve("a.txt"), "changed again");
        git(main, "stash", "push");
        GitMetadata pushedTwice = metadata(main);
        git(main, "stash", "drop");

        assertNotEquals(before.stashes(), pushed.stashes());
        assertNotEquals(pushed.stashes(), pushedTwice.stashes());
        assertNotEquals(pushedTwice.stashes(), metadata(main).stashes());
    }

    @Test
    void addingAndLockingAWorktreeChangesTheWorktrees() throws Exception {
        GitMetadata before = metadata(main);

        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata added = metadata(main);
        git(main, "worktree", "lock", linked.toString());

        assertNotEquals(before.worktrees(), added.worktrees());
        assertNotEquals(added.worktrees(), metadata(main).worktrees());
    }

    @Test
    void aLinkedWorktreeSeesTheCommitsOfTheOthers() throws Exception {
        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata fromLinked = metadata(linked);
        commit(main, "b.txt");
        GitMetadata fromMain = metadata(main);
        commit(linked, "c.txt");

        assertNotEquals(fromLinked.branches(), metadata(linked).branches());
        assertNotEquals(fromMain.branches(), metadata(main).branches());
        assertEquals(fromMain.worktrees(), metadata(main).worktrees());
    }

    @Test
    void checkingOutInALinkedWorktreeChangesTheWorktreesOfTheMainOne() throws Exception {
        git(main, "branch", "other");
        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata before = metadata(main);

        git(linked, "checkout", "other");

        assertNotEquals(before.worktrees(), metadata(main).worktrees());
    }

    @Test
    void checkingOutInALinkedWorktreeChangesTheBranchesOfTheMainOne() throws Exception {
        git(main, "branch", "other");
        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        GitMetadata before = metadata(main);

        git(linked, "checkout", "other");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void aStampWithAFileModifiedJustNowTellsNothing() throws Exception {
        commit(main, "b.txt");

        // Right when the commit moved the branch, however long the test took to get here.
        Instant committed =
                Files.getLastModifiedTime(main.resolve(".git/refs/heads")).toInstant();

        GitMetadata justNow = CliMetadata.read(CliMetadata.locate(main).get(), committed);

        assertEquals(Option.none(), justNow.branches());
        assertTrue(metadata(main).branches().isDefined());
    }

    @Test
    void aRemoteTrackingBranchThatMovesChangesTheBranches() throws Exception {
        commit(main, "b.txt");
        git(main, "update-ref", "refs/remotes/origin/main", "HEAD");
        GitMetadata before = metadata(main);

        git(main, "update-ref", "refs/remotes/origin/main", "HEAD~1");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void deletingABranchInADirectoryOfRefsChangesTheBranches() throws Exception {
        git(main, "branch", "feature/deep/x");
        GitMetadata before = metadata(main);

        git(main, "branch", "-D", "feature/deep/x");

        assertNotEquals(before.branches(), metadata(main).branches());
    }

    @Test
    void clearingTheStashesChangesThem() throws Exception {
        Files.writeString(main.resolve("a.txt"), "changed");
        git(main, "stash", "push");
        GitMetadata before = metadata(main);

        git(main, "stash", "clear");

        assertNotEquals(before.stashes(), metadata(main).stashes());
    }

    @Test
    void unlockingAndRemovingAWorktreeChangesTheWorktrees() throws Exception {
        Path linked = dir.resolve("linked");
        git(main, "worktree", "add", "-b", "feature", linked.toString());
        git(main, "worktree", "lock", linked.toString());
        GitMetadata locked = metadata(main);

        git(main, "worktree", "unlock", linked.toString());
        GitMetadata unlocked = metadata(main);
        git(main, "worktree", "remove", linked.toString());

        assertNotEquals(locked.worktrees(), unlocked.worktrees());
        assertNotEquals(unlocked.worktrees(), metadata(main).worktrees());
    }

    @Test
    void aReftableRepositoryTellsItsCommitsAndStashes() throws Exception {
        Path reftable = dir.resolve("reftable");
        assumeTrue(reftableSupported(reftable));
        commit(reftable, "a.txt");
        GitMetadata before = metadata(reftable);

        commit(reftable, "b.txt");
        GitMetadata committed = metadata(reftable);
        Files.writeString(reftable.resolve("a.txt"), "changed");
        git(reftable, "stash", "push");

        assertNotEquals(before.branches(), committed.branches());
        assertNotEquals(committed.stashes(), metadata(reftable).stashes());
    }

    @Test
    void aDirectoryWithoutAGitDirectoryHasNothingToLookAt() throws Exception {
        Path plain = Files.createDirectories(dir.resolve("plain"));

        assertEquals(Option.none(), CliMetadata.locate(plain));
    }

    @Test
    void anOpenedRepositoryTellsItsStamps() {
        assertTrue(CliGitInfoProvider.open(main).get().metadata().isDefined());
    }
}
