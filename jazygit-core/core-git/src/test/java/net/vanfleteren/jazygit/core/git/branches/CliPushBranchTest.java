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
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pushing the branches of a real repository to a remote, as its push.default says, and deleting them there.
 */
class CliPushBranchTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void pushPublishesABranchWithoutUpstreamAndThenSendsNewCommits() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());

        repository
                .remotes()
                .pushSettingUpstream(new BranchName("main"), new RemoteBranch("origin", new BranchName("main")))
                .get();
        assertEquals(List.of(0, 0), counts("main"));
        assertTrue(repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .filter(b -> b.name().equals(new BranchName("main")))
                .forAll(Branch::hasRemote));

        git("commit", "-q", "--allow-empty", "-m", "local");
        assertEquals(List.of(1, 0), counts("main"));
        repository
                .remotes()
                .push(new BranchName("main"), new RemoteBranch("origin", new BranchName("main")), PushMode.FAST_FORWARD)
                .get();
        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void withPushDefaultUpstreamAnUpstreamUnderAnotherNameIsWherePushingAndDeletingGo() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "mirror", bare.toString());
        git("config", "push.default", "upstream");

        assertEquals(
                List.of("git push --set-upstream mirror refs/heads/main:refs/heads/shared"),
                repository
                        .remotes()
                        .pushSettingUpstream(
                                new BranchName("main"), new RemoteBranch("mirror", new BranchName("shared")))
                        .get()
                        .map(ExecutedCommand::line));
        Branch main = repository.branches().all().map(AllBranches::local).get().head();
        assertEquals(
                Option.of(
                        new Upstream(new BranchRef.Remote(new RemoteBranch("mirror", new BranchName("shared"))), 0, 0)),
                main.upstream());
        assertTrue(main.hasRemote());

        git("commit", "-q", "--allow-empty", "-m", "local");
        assertEquals(
                List.of("git push mirror refs/heads/main:refs/heads/shared"),
                repository
                        .remotes()
                        .push(new BranchName("main"), main.pushTarget(), PushMode.FAST_FORWARD)
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(List.of(0, 0), counts("main"));

        assertEquals(
                List.of("git push mirror --delete refs/heads/shared"),
                repository
                        .remotes()
                        .deleteBranch(new BranchName("main"), main.pushTarget(), DeleteScope.REMOTE)
                        .get()
                        .map(ExecutedCommand::line));
        assertTrue(gitSucceedsIn(repo, "fetch", "-q", "--prune", "mirror"));
        assertEquals(
                Option.none(),
                repository.branches().all().map(AllBranches::local).get().head().upstream());
    }

    @Test
    void deletingABranchOnItsRemoteLeavesATagOfTheSameNameThere() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "HEAD:refs/heads/feature", "HEAD:refs/tags/feature");

        repository
                .remotes()
                .deleteRemoteBranch(new RemoteBranch("origin", new BranchName("feature")))
                .get();

        assertFalse(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/heads/feature"));
        assertTrue(gitSucceedsIn(repo, "ls-remote", "--exit-code", "origin", "refs/tags/feature"));
    }

    @Test
    void aTagOfTheSameNameDoesNotKeepABranchFromBeingPushed() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        RemoteBranch main = new RemoteBranch("origin", new BranchName("main"));
        repository.remotes().pushSettingUpstream(new BranchName("main"), main).get();
        git("tag", "main", "HEAD");
        git("commit", "-q", "--allow-empty", "-m", "local");

        assertEquals(
                List.of("git push origin refs/heads/main:refs/heads/main"),
                repository
                        .remotes()
                        .push(new BranchName("main"), main, PushMode.FAST_FORWARD)
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void withPushDefaultSimpleAnUpstreamUnderAnotherNameIsNotPushedTo() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "mirror", bare.toString());
        git("config", "push.default", "simple");
        RemoteBranch shared = new RemoteBranch("mirror", new BranchName("shared"));
        repository.remotes().pushSettingUpstream(new BranchName("main"), shared).get();
        git("commit", "-q", "--allow-empty", "-m", "local");

        GitFailure refused = repository
                .remotes()
                .push(new BranchName("main"), shared, PushMode.FAST_FORWARD)
                .getLeft();

        assertEquals(new GitError.PushRefused("simple", shared), refused.error());
        assertEquals(List.of(), refused.commands());
        assertEquals(List.of(1, 0), counts("main"));
    }

    @Test
    void withPushDefaultNothingNoBranchIsPushedWithoutBeingToldWhere() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        RemoteBranch main = new RemoteBranch("origin", new BranchName("main"));
        repository.remotes().pushSettingUpstream(new BranchName("main"), main).get();
        git("config", "push.default", "nothing");
        git("commit", "-q", "--allow-empty", "-m", "local");

        assertEquals(
                new GitError.PushRefused("nothing", main),
                repository
                        .remotes()
                        .push(new BranchName("main"), main, PushMode.FAST_FORWARD)
                        .getLeft()
                        .error());
        assertEquals(List.of(1, 0), counts("main"));
    }

    @Test
    void aPushDefaultGitDoesNotKnowPushesNothing() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        RemoteBranch main = new RemoteBranch("origin", new BranchName("main"));
        repository.remotes().pushSettingUpstream(new BranchName("main"), main).get();
        git("commit", "-q", "--allow-empty", "-m", "local");
        // Git fails most commands on a value it does not know, so it is set last.
        git("config", "push.default", "sideways");

        assertEquals(
                new GitError.PushRefused("sideways", main),
                repository
                        .remotes()
                        .push(new BranchName("main"), main, PushMode.FAST_FORWARD)
                        .getLeft()
                        .error());
    }

    @Test
    void aFailedPushSetsNoUpstream() throws Exception {
        assertTrue(repository
                .remotes()
                .pushSettingUpstream(new BranchName("main"), new RemoteBranch("nowhere", new BranchName("main")))
                .isLeft());

        assertEquals(
                Option.none(),
                repository.branches().all().map(AllBranches::local).get().head().upstream());
        assertFalse(gitSucceedsIn(repo, "config", "--get", "branch.main.merge"));
    }

    @Test
    void aRewrittenBranchOnlyPushesWithForceWithLease() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        git("commit", "-q", "--allow-empty", "-m", "ours");
        git("push", "-q", "origin", "main");

        // The commit pushed is amended here.
        git("commit", "-q", "--amend", "--allow-empty", "-m", "ours, amended");
        assertEquals(List.of(1, 1), counts("main"));

        assertTrue(repository
                .remotes()
                .push(new BranchName("main"), new RemoteBranch("origin", new BranchName("main")), PushMode.FAST_FORWARD)
                .isLeft());

        repository
                .remotes()
                .push(
                        new BranchName("main"),
                        new RemoteBranch("origin", new BranchName("main")),
                        PushMode.FORCE_WITH_LEASE)
                .get();
        git("fetch", "-q", "origin");
        assertEquals(List.of(0, 0), counts("main"));
    }

    @Test
    void aForcePushDoesNotDropWhatSomeoneElsePushedOnceItWasFetched() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");

        // The remote gets a commit that this repository fetches, while the local branch gets one of its own: the
        // lease holds, as origin/main is where the remote is, but the branch never had that commit.
        java.nio.file.Path other = Files.createTempDirectory("other");
        gitIn(other, "clone", "-q", bare.toString(), "clone");
        gitIn(other.resolve("clone"), "commit", "-q", "--allow-empty", "-m", "theirs");
        gitIn(other.resolve("clone"), "push", "-q", "origin", "main");
        git("fetch", "-q", "origin");
        git("commit", "-q", "--allow-empty", "-m", "ours");
        assertEquals(List.of(1, 1), counts("main"));

        assertTrue(repository
                .remotes()
                .push(
                        new BranchName("main"),
                        new RemoteBranch("origin", new BranchName("main")),
                        PushMode.FORCE_WITH_LEASE)
                .isLeft());
        git("fetch", "-q", "origin");
        assertEquals(List.of(1, 1), counts("main"));
    }

    @Test
    void pushFailsWithGitsExplanationWithoutARemote() {
        assertTrue(repository
                .remotes()
                .push(new BranchName("main"), new RemoteBranch("origin", new BranchName("main")), PushMode.FAST_FORWARD)
                .isLeft());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private List<Integer> counts(String branch) {
        return CliGitFixture.counts(repository, branch);
    }
}
