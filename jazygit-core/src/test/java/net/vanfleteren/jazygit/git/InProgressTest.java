package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * What the repository is in the middle of, as the status reports it, and continuing, aborting or skipping it.
 */
class InProgressTest {

    @TempDir
    Path repo;

    private GitInfoProvider git;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        write("a.txt", "one\n");
        git("add", ".");
        git("commit", "-qm", "init");
        git("checkout", "-qb", "conflicting");
        write("a.txt", "conflicting\n");
        git("commit", "-qam", "conflicting changes a");
        git("checkout", "-q", "main");
        write("a.txt", "main\n");
        git("commit", "-qam", "main changes a");
        git = CliGitInfoProvider.open(repo).get();
    }

    @Test
    void nothingIsInProgressInAQuietRepository() {
        assertEquals(Option.none(), git.status().get().inProgress());
    }

    @Test
    void aStoppedMergeIsReportedWithTheCommitBeingMerged() throws Exception {
        gitAnyExit("merge", "conflicting");

        assertEquals(
                Option.of(new InProgress.Merging(sha("conflicting"))),
                git.status().get().inProgress());
    }

    @Test
    void aStoppedRebaseIsReportedWithItsBranchWhereItIsOntoAndHowFarItGot() throws Exception {
        git("checkout", "-q", "conflicting");
        write("b.txt", "b\n");
        git("add", ".");
        git("commit", "-qm", "add b");
        gitAnyExit("rebase", "main");

        assertEquals(
                Option.of(new InProgress.Rebasing(Option.of(new Branch.Name("conflicting")), sha("main"), 1, 2)),
                git.status().get().inProgress());
    }

    @Test
    void aStoppedCherryPickIsReportedWithTheCommitBeingPicked() throws Exception {
        gitAnyExit("cherry-pick", "conflicting");

        assertEquals(
                Option.of(new InProgress.CherryPicking(sha("conflicting"))),
                git.status().get().inProgress());
    }

    @Test
    void aStoppedRevertIsReportedWithTheCommitBeingReverted() throws Exception {
        write("a.txt", "later\n");
        git("commit", "-qam", "later changes a");
        gitAnyExit("revert", "--no-edit", "main~1");

        assertEquals(
                Option.of(new InProgress.Reverting(sha("main~1"))),
                git.status().get().inProgress());
    }

    @Test
    void continuingAResolvedMergeCommitsItWithGitsMessage() throws Exception {
        gitAnyExit("merge", "conflicting");
        InProgress merging = git.status().get().inProgress().get();
        write("a.txt", "resolved\n");
        git("add", "a.txt");

        assertEquals(
                List.of("git merge --continue"),
                git.continueOrAbort(merging, InProgressAction.CONTINUE).get().map(ExecutedCommand::line));

        assertEquals(Option.none(), git.status().get().inProgress());
        assertEquals(
                "Merge branch 'conflicting'", git("log", "-1", "--format=%s").strip());
    }

    @Test
    void continuingARebaseThatStopsOnTheNextConflictReportsTheStop() throws Exception {
        git("checkout", "-q", "conflicting");
        write("a.txt", "again\n");
        git("commit", "-qam", "changes a again");
        gitAnyExit("rebase", "main");
        InProgress rebasing = git.status().get().inProgress().get();
        write("a.txt", "resolved\n");
        git("add", "a.txt");

        GitFailure<GitError> failure =
                git.continueOrAbort(rebasing, InProgressAction.CONTINUE).getLeft();

        InProgress next = new InProgress.Rebasing(Option.of(new Branch.Name("conflicting")), sha("main"), 2, 2);
        assertEquals(new GitError.StoppedOnConflicts(Option.of(next), 1), failure.error());
        assertEquals(List.of("git rebase --continue"), failure.commands().map(ExecutedCommand::line));
        assertEquals(Option.of(next), git.status().get().inProgress());
    }

    @Test
    void continuingWhileConflictsRemainFails() throws Exception {
        gitAnyExit("merge", "conflicting");
        InProgress merging = git.status().get().inProgress().get();

        GitFailure<GitError> failure =
                git.continueOrAbort(merging, InProgressAction.CONTINUE).getLeft();

        assertEquals(List.of("git merge --continue"), failure.commands().map(ExecutedCommand::line));
        assertEquals(Option.of(merging), git.status().get().inProgress());
    }

    @Test
    void abortingARebasePutsTheBranchBackAsItWas() throws Exception {
        git("checkout", "-q", "conflicting");
        String before = git("rev-parse", "HEAD");
        gitAnyExit("rebase", "main");

        assertEquals(
                List.of("git rebase --abort"),
                git.continueOrAbort(git.status().get().inProgress().get(), InProgressAction.ABORT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(Option.none(), git.status().get().inProgress());
        assertEquals(before, git("rev-parse", "conflicting"));
        assertEquals("conflicting\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void abortingAnAmendThatStoppedOnConflictsKeepsTheChangesStaged() throws Exception {
        String before = git("rev-parse", "HEAD");
        write("a.txt", "amended\n");
        git("add", "a.txt");
        // The staged change is to what the last commit made of a.txt, which the first one does not have.
        GitFailure<GitError> stop = git.amendCommit(sha("main~1")).getLeft();
        assertInstanceOf(GitError.StoppedOnConflicts.class, stop.error());

        assertEquals(
                List.of("git rebase --abort", "git reset --quiet --soft HEAD^"),
                git.continueOrAbort(git.status().get().inProgress().get(), InProgressAction.ABORT)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(Option.none(), git.status().get().inProgress());
        assertEquals(before, git("rev-parse", "HEAD"));
        assertEquals("M  a.txt\n", git("status", "--porcelain"));
        assertEquals("amended\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void skippingACherryPickLeavesOutItsCommit() throws Exception {
        String before = git("rev-parse", "HEAD");
        gitAnyExit("cherry-pick", "conflicting");

        assertEquals(
                List.of("git cherry-pick --skip"),
                git.continueOrAbort(git.status().get().inProgress().get(), InProgressAction.SKIP)
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(Option.none(), git.status().get().inProgress());
        assertEquals(before, git("rev-parse", "HEAD"));
        assertEquals("", git("status", "--porcelain"));
    }

    private Sha sha(String rev) throws Exception {
        return new Sha(git("rev-parse", rev).strip());
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
