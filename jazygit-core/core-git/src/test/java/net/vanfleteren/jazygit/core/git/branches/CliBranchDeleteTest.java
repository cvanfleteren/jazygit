package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deleting the branches of a real repository, and counting the commits only a branch has before that.
 */
class CliBranchDeleteTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void deleteBranchRemovesTheLocalBranch() throws Exception {
        git("branch", "topic");

        repository
                .remotes()
                .deleteBranch(
                        new BranchName("topic"), new RemoteBranch("origin", new BranchName("topic")), DeleteScope.LOCAL)
                .get();

        assertEquals(
                List.of("main"),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::local)
                        .get()
                        .map(b -> b.name().value())
                        .toList());
    }

    @Test
    void deleteLocalBranchRefusesAnUnmergedBranchAndKeepsIt() throws Exception {
        commitOnTopic("topic.txt");

        GitFailure failure =
                repository.branches().deleteLocal(new BranchName("topic")).getLeft();

        assertInstanceOf(GitError.NotFullyMerged.class, failure.error());
        assertEquals(List.of("git branch --delete -- topic"), failure.commands().map(ExecutedCommand::line));
        assertEquals(List.of("main", "topic"), localBranches());
    }

    @Test
    void deleteBranchRefusesAnUnmergedBranchLocallyToo() throws Exception {
        commitOnTopic("topic.txt");

        assertInstanceOf(
                GitError.NotFullyMerged.class,
                repository
                        .remotes()
                        .deleteBranch(
                                new BranchName("topic"),
                                new RemoteBranch("origin", new BranchName("topic")),
                                DeleteScope.LOCAL)
                        .getLeft()
                        .error());
        assertEquals(List.of("main", "topic"), localBranches());
    }

    @Test
    void deleteLocalBranchRemovesAMergedBranch() throws Exception {
        commitOnTopic("topic.txt");
        git("merge", "-q", "--ff-only", "topic");

        assertEquals(
                List.of("git branch --delete -- topic"),
                repository.branches().deleteLocal(new BranchName("topic")).get().map(ExecutedCommand::line));
        assertEquals(List.of("main"), localBranches());
    }

    @Test
    void forceDeleteLocalBranchRemovesAnUnmergedBranchWithoutTouchingTheRemote() throws Exception {
        commitOnTopic("topic.txt");

        assertEquals(
                List.of("git branch --delete --force -- topic"),
                repository
                        .branches()
                        .forceDeleteLocal(new BranchName("topic"))
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(List.of("main"), localBranches());
    }

    @Test
    void countOnlyOnCountsTheCommitsNoOtherRefHas() throws Exception {
        commitOnTopic("one.txt");
        Files.writeString(repo.resolve("two.txt"), "two\n");
        git("checkout", "-q", "topic");
        git("add", "two.txt");
        git("commit", "-q", "-m", "Two");
        git("checkout", "-q", "main");

        assertEquals(
                2, repository.branches().countOnlyOn(new BranchName("topic")).get());

        git("tag", "kept", "topic~1");
        assertEquals(
                1, repository.branches().countOnlyOn(new BranchName("topic")).get());

        git("branch", "other", "topic");
        assertEquals(
                0, repository.branches().countOnlyOn(new BranchName("topic")).get());
    }

    @Test
    void countOnlyOnIsNoneForABranchWhoseCommitsAreOnARemoteBranch() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", bare.toString());
        git("remote", "add", "origin", bare.toString());
        commitOnTopic("topic.txt");
        git("push", "-q", "origin", "topic");

        assertEquals(
                0, repository.branches().countOnlyOn(new BranchName("topic")).get());
        assertInstanceOf(
                GitError.NotFullyMerged.class,
                repository
                        .branches()
                        .deleteLocal(new BranchName("topic"))
                        .getLeft()
                        .error());
    }

    @Test
    void deletingTheBranchAndItsRemoteBranchLeavesCommitsOnlyItHadToBeCounted() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", bare.toString());
        git("remote", "add", "origin", bare.toString());
        commitOnTopic("topic.txt");
        git("push", "-q", "origin", "topic");
        assertEquals(
                0, repository.branches().countOnlyOn(new BranchName("topic")).get());

        GitFailure failure = repository
                .remotes()
                .deleteBranch(
                        new BranchName("topic"), new RemoteBranch("origin", new BranchName("topic")), DeleteScope.BOTH)
                .getLeft();

        assertInstanceOf(GitError.NotFullyMerged.class, failure.error());
        assertEquals(
                1, repository.branches().countOnlyOn(new BranchName("topic")).get());
        assertEquals(List.of("main", "topic"), localBranches());
    }

    /**
     * Commits {@code file} on a new branch {@code topic}, which main does not have, and checks main out again.
     */
    private void commitOnTopic(String file) throws Exception {
        git("checkout", "-q", "-b", "topic");
        Files.writeString(repo.resolve(file), file + "\n");
        git("add", file);
        git("commit", "-q", "-m", file);
        git("checkout", "-q", "main");
    }

    private List<String> localBranches() {
        return repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .map(b -> b.name().value())
                .sorted()
                .toList();
    }

    @Test
    void deleteBranchFailsForTheCheckedOutBranch() {
        assertTrue(repository
                .remotes()
                .deleteBranch(
                        new BranchName("main"), new RemoteBranch("origin", new BranchName("main")), DeleteScope.LOCAL)
                .isLeft());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }
}
