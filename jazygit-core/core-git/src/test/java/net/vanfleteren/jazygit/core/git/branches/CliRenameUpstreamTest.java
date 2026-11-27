package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitSucceedsIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renaming the upstream of a branch of a real repository on its remote.
 */
class CliRenameUpstreamTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    private static final RemoteBranch FEATURE_ON_ORIGIN = new RemoteBranch("origin", new BranchName("feature"));

    @Test
    void renamingTheUpstreamMovesItOnTheRemoteWithoutTheUnpushedCommits() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        RemoteBranch feature = new RemoteBranch("origin", new BranchName("feature"));
        git("branch", "feature");
        repository
                .remotes()
                .pushSettingUpstream(new BranchName("feature"), feature)
                .get();
        Sha pushed = tip("feature");
        git("checkout", "-q", "feature");
        git("commit", "-q", "--allow-empty", "-m", "unpushed");
        git("branch", "--move", "feature", "topic");

        assertEquals(
                List.of(
                        "git fetch --prune --no-write-fetch-head origin",
                        "git push --atomic --force-with-lease=refs/heads/topic: --force-with-lease=refs/heads/feature"
                                + " origin refs/remotes/origin/feature:refs/heads/topic :refs/heads/feature",
                        "git config --local branch.topic.merge refs/heads/topic"),
                repository
                        .remotes()
                        .renameUpstream(new BranchName("topic"), feature)
                        .get()
                        .map(ExecutedCommand::line));
        assertFalse(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/heads/feature"));
        assertEquals(
                pushed.value(), gitIn(bare, "rev-parse", "refs/heads/topic").strip());
        assertEquals(
                Option.of(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("topic")))),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::local)
                        .get()
                        .find(branch -> branch.name().value().equals("topic"))
                        .flatMap(Branch::upstream)
                        .map(Upstream::branch));
        assertEquals(List.of(1, 0), counts("topic"));
    }

    @Test
    void renamingTheUpstreamOntoABranchOnTheRemoteChangesNothing() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        RemoteBranch feature = new RemoteBranch("origin", new BranchName("feature"));
        git("branch", "feature");
        repository
                .remotes()
                .pushSettingUpstream(new BranchName("feature"), feature)
                .get();
        git("commit", "-q", "--allow-empty", "-m", "other");
        git("push", "-q", "origin", "HEAD:refs/heads/topic");
        git("branch", "--move", "feature", "topic");

        GitFailure failure = repository
                .remotes()
                .renameUpstream(new BranchName("topic"), feature)
                .getLeft();

        assertTrue(
                failure.error() instanceof GitError.UpstreamNotRenamed(var upstream, var newName, var cause)
                        && upstream.equals(feature)
                        && newName.value().equals("topic"),
                failure.error()::toString);
        assertTrue(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/heads/feature"));
        assertEquals(
                tip("main").value(),
                gitIn(bare, "rev-parse", "refs/heads/topic").strip());
        assertEquals(
                "refs/heads/feature",
                git("config", "--get", "branch.topic.merge").strip());
    }

    @Test
    void renamingTheUpstreamOntoABranchOnTheRemoteAtTheSameCommitRenamesIt() throws Exception {
        java.nio.file.Path bare = renamedFeature();
        git("push", "-q", "origin", "refs/remotes/origin/feature:refs/heads/topic");

        repository
                .remotes()
                .renameUpstream(new BranchName("topic"), FEATURE_ON_ORIGIN)
                .get();

        assertFalse(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/heads/feature"));
        assertEquals(
                gitIn(bare, "rev-parse", "refs/heads/main").strip(),
                gitIn(bare, "rev-parse", "refs/heads/topic").strip());
        assertEquals(
                "refs/heads/topic", git("config", "--get", "branch.topic.merge").strip());
    }

    @Test
    void anUpstreamGoneFromTheRemoteIsSaidToBeGoneAndNothingIsPushed() throws Exception {
        java.nio.file.Path bare = renamedFeature();
        gitIn(bare, "branch", "--delete", "--force", "feature");

        GitFailure failure = repository
                .remotes()
                .renameUpstream(new BranchName("topic"), FEATURE_ON_ORIGIN)
                .getLeft();

        assertEquals(new GitError.UpstreamNotOnRemote(FEATURE_ON_ORIGIN, new BranchName("topic")), failure.error());
        assertEquals(
                List.of("git fetch --prune --no-write-fetch-head origin"),
                failure.commands().map(ExecutedCommand::line));
        assertFalse(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/heads/topic"));
    }

    @Test
    void whenThePushMakesNoRemoteTrackingBranchTheBranchStillFollowsTheRenamedOne() throws Exception {
        java.nio.file.Path bare = renamedFeature();
        // A remote that only fetches feature, as a clone with --single-branch does for its branch.
        git("config", "remote.origin.fetch", "+refs/heads/feature:refs/remotes/origin/feature");

        repository
                .remotes()
                .renameUpstream(new BranchName("topic"), FEATURE_ON_ORIGIN)
                .get();

        assertFalse(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/heads/feature"));
        assertTrue(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/heads/topic"));
        assertEquals("origin", git("config", "--get", "branch.topic.remote").strip());
        assertEquals(
                "refs/heads/topic", git("config", "--get", "branch.topic.merge").strip());
        assertEquals(
                bare.toString(), git("config", "--get", "remote.origin.url").strip());
    }

    /**
     * A bare remote {@code origin} with {@code feature}, which a local branch followed and was renamed from to
     * {@code topic}, which still follows it.
     */
    private java.nio.file.Path renamedFeature() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "main");
        git("branch", "feature");
        repository
                .remotes()
                .pushSettingUpstream(new BranchName("feature"), FEATURE_ON_ORIGIN)
                .get();
        git("branch", "--move", "feature", "topic");
        return bare;
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Sha tip(String branch) throws IOException, InterruptedException, TimeoutException {
        return CliGitFixture.tip(repo, branch);
    }

    private List<Integer> counts(String branch) {
        return CliGitFixture.counts(repository, branch);
    }
}
