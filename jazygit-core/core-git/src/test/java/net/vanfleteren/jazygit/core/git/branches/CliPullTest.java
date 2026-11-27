package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitSucceedsIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.PullMode;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pulling into the branches of a real repository, and fast-forwarding them.
 */
class CliPullTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    /**
     * A remote that main is pushed to and tracks, and a clone of it in which "theirs" is pushed to
     * {@code branch}; the repository has fetched that commit.
     */
    private Path remoteWithTheirCommitOn(String branch) throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        java.nio.file.Path clone = Files.createTempDirectory("other").resolve("clone");
        gitIn(clone.getParent(), "clone", "-q", bare.toString(), "clone");
        gitIn(clone, "checkout", "-q", "-B", branch);
        Files.writeString(clone.resolve("README.md"), "theirs\n");
        gitIn(clone, "commit", "-q", "-am", "theirs");
        gitIn(clone, "push", "-q", "origin", branch);
        git("fetch", "-q", "origin");
        return clone;
    }

    @Test
    void pullMergesTheUpstreamWithoutAskingForAMessage() throws Exception {
        remoteWithTheirCommitOn("main");
        git("config", "pull.rebase", "false");
        git("config", "pull.ff", "true");
        git("commit", "-q", "--allow-empty", "-m", "ours");
        assertEquals(List.of(1, 1), counts("main"));

        assertEquals(
                List.of("git pull --no-edit"),
                repository.remotes().pull(PullMode.CONFIGURED).get().map(ExecutedCommand::line));

        assertEquals(List.of(2, 0), counts("main"));
        assertEquals("theirs\n", Files.readString(repo.resolve("README.md")));
    }

    @Test
    void aPullThatConflictsIsLeftForTheUserToResolve() throws Exception {
        remoteWithTheirCommitOn("main");
        git("config", "pull.rebase", "false");
        git("config", "pull.ff", "true");
        Files.writeString(repo.resolve("README.md"), "ours\n");
        git("commit", "-q", "-am", "ours");

        GitFailure failure = repository.remotes().pull(PullMode.CONFIGURED).getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertEquals(List.of("git pull --no-edit"), failure.commands().map(ExecutedCommand::line));
        assertTrue(gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "MERGE_HEAD"));
    }

    @Test
    void aRebasingPullThatConflictsIsLeftForTheUserToResolve() throws Exception {
        remoteWithTheirCommitOn("main");
        git("config", "pull.rebase", "true");
        Files.writeString(repo.resolve("README.md"), "ours\n");
        git("commit", "-q", "-am", "ours");

        GitFailure failure = repository.remotes().pull(PullMode.CONFIGURED).getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertEquals(List.of("git pull --no-edit"), failure.commands().map(ExecutedCommand::line));
        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void fastForwardMovesABranchThatIsNotCheckedOutToItsUpstream() throws Exception {
        remoteWithTheirCommitOn("main");
        git("checkout", "-q", "-b", "other");
        assertEquals(List.of(0, 1), counts("main"));

        assertEquals(
                List.of("git fetch origin refs/heads/main:refs/heads/main"),
                repository
                        .remotes()
                        .fastForward(
                                new BranchName("main"),
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))))
                        .get()
                        .map(ExecutedCommand::line));

        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void pullingARebaseReplaysOurCommitsWhateverTheSettingsSay() throws Exception {
        remoteWithTheirCommitOn("main");
        git("commit", "-q", "--allow-empty", "-m", "ours");

        assertEquals(
                List.of("git pull --rebase"),
                repository.remotes().pull(PullMode.REBASE).get().map(ExecutedCommand::line));

        assertEquals(List.of(1, 0), counts("main"));
        assertEquals("ours", git("log", "-1", "--format=%s").strip());
    }

    @Test
    void pullingAMergeMakesAMergeCommitWhateverTheSettingsSay() throws Exception {
        remoteWithTheirCommitOn("main");
        git("commit", "-q", "--allow-empty", "-m", "ours");

        assertEquals(
                List.of("git pull --no-rebase --no-edit"),
                repository.remotes().pull(PullMode.MERGE).get().map(ExecutedCommand::line));

        assertEquals(List.of(2, 0), counts("main"));
        assertEquals("1", git("rev-list", "--merges", "--count", "HEAD").strip());
    }

    @Test
    void aRebasingPullThatConflictsLeavesTheRebaseForTheUser() throws Exception {
        remoteWithTheirCommitOn("main");
        Files.writeString(repo.resolve("README.md"), "ours\n");
        git("commit", "-q", "-am", "ours");

        GitFailure failure = repository.remotes().pull(PullMode.REBASE).getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertTrue(Files.exists(repo.resolve(".git/rebase-merge")));
    }

    @Test
    void aMergingPullThatConflictsLeavesTheMergeForTheUser() throws Exception {
        remoteWithTheirCommitOn("main");
        Files.writeString(repo.resolve("README.md"), "ours\n");
        git("commit", "-q", "-am", "ours");

        GitFailure failure = repository.remotes().pull(PullMode.MERGE).getLeft();

        assertInstanceOf(GitError.StoppedOnConflicts.class, failure.error());
        assertTrue(gitSucceedsIn(repo, "rev-parse", "--verify", "--quiet", "MERGE_HEAD"));
    }

    @Test
    void thePullIsConfiguredWhenPullRebaseOrPullFfIsSet() throws Exception {
        assertEquals(
                false,
                repository.remotes().pullConfigured(new BranchName("main")).get());

        git("config", "pull.ff", "only");
        assertEquals(
                true,
                repository.remotes().pullConfigured(new BranchName("main")).get());

        git("config", "--unset", "pull.ff");
        git("config", "pull.rebase", "false");
        assertEquals(
                true,
                repository.remotes().pullConfigured(new BranchName("main")).get());

        git("config", "--unset", "pull.rebase");
        assertEquals(
                false,
                repository.remotes().pullConfigured(new BranchName("main")).get());
        git("config", "branch.main.rebase", "true");
        assertEquals(
                true,
                repository.remotes().pullConfigured(new BranchName("main")).get());
        assertEquals(
                false,
                repository.remotes().pullConfigured(new BranchName("other")).get());
    }

    @Test
    void fastForwardRefusesABranchThatHasDiverged() throws Exception {
        remoteWithTheirCommitOn("main");
        git("commit", "-q", "--allow-empty", "-m", "ours");
        git("checkout", "-q", "-b", "other");

        assertTrue(repository
                .remotes()
                .fastForward(
                        new BranchName("main"),
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))))
                .isLeft());
        assertEquals(List.of(1, 1), counts("main"));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private List<Integer> counts(String branch) {
        return CliGitFixture.counts(repository, branch);
    }
}
