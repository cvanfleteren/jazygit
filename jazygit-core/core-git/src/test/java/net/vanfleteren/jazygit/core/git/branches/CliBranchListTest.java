package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static net.vanfleteren.jazygit.core.git.CliGitFixture.withoutTimes;
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
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Listing the branches of a real repository: what they follow, where they are on their remote, and which is the default.
 */
class CliBranchListTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void branchesFollowExternalChanges() throws Exception {
        Sha head = repository.status().read().get().headCommit().get();
        assertEquals(
                List.of(Fixtures.branch(new BranchName("main"), true, head)),
                withoutTimes(repository.branches().all().map(AllBranches::local).get()));

        git("branch", "foo");
        assertEquals(
                List.of(
                        Fixtures.branch(new BranchName("main"), true, head),
                        Fixtures.branch(new BranchName("foo"), false, head)),
                withoutTimes(repository.branches().all().map(AllBranches::local).get()));

        git("checkout", "-q", "foo");
        assertEquals(
                List.of(
                        Fixtures.branch(new BranchName("foo"), true, head),
                        Fixtures.branch(new BranchName("main"), false, head)),
                withoutTimes(repository.branches().all().map(AllBranches::local).get()));

        git("commit", "-q", "--allow-empty", "-m", "Move foo");
        Sha moved = repository.status().read().get().headCommit().get();
        assertEquals(
                List.of(
                        Fixtures.branch(new BranchName("foo"), true, moved),
                        Fixtures.branch(new BranchName("main"), false, head)),
                withoutTimes(repository.branches().all().map(AllBranches::local).get()));
    }

    @Test
    void aBranchThatFollowsALocalOneIsReadSoAndFastForwardedToIt() throws Exception {
        git("branch", "-q", "--track", "topic", "main");
        git("commit", "-q", "--allow-empty", "-m", "Ahead");
        BranchRef main = new BranchRef.Local(new BranchName("main"));

        Branch topic = repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .find(b -> b.name().equals(new BranchName("topic")))
                .get();
        assertEquals(Option.of(new Upstream(main, 0, 1)), topic.upstream());
        assertEquals(Option.none(), topic.remoteUpstream());

        assertEquals(
                List.of("git fetch . refs/heads/main:refs/heads/topic"),
                repository
                        .remotes()
                        .fastForward(new BranchName("topic"), main)
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(
                0,
                repository
                        .history()
                        .countNotOn(main, new BranchRef.Local(new BranchName("topic")), 1000)
                        .get());
    }

    @Test
    void aBranchThatFollowsADeletedLocalOneIsPushedToOrigin() throws Exception {
        git("branch", "-q", "--track", "topic", "main");
        git("checkout", "-q", "-b", "other");
        git("branch", "-q", "-D", "main");

        Branch topic = repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .find(b -> b.name().equals(new BranchName("topic")))
                .get();
        assertEquals(Option.of(new BranchRef.Local(new BranchName("main"))), topic.goneUpstream());
        assertEquals(Option.of("main"), topic.goneUpstream().map(BranchRef::name));
        assertEquals(new RemoteBranch("origin", new BranchName("topic")), topic.pushTarget());
    }

    @Test
    void aBranchThatFollowsALocalOneIsOnOriginUnderItsOwnName() throws Exception {
        git("branch", "-q", "--track", "topic", "main");
        git("update-ref", "refs/remotes/origin/topic", "HEAD");

        Branch topic = repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .find(b -> b.name().equals(new BranchName("topic")))
                .get();
        assertEquals(new RemoteBranch("origin", new BranchName("topic")), topic.pushTarget());
        assertTrue(topic.hasRemote());
    }

    @Test
    void branchesKnowWhetherTheyExistOnTheirRemote() throws Exception {
        git("branch", "pushed");
        git("branch", "local-only");
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "pushed");

        assertEquals(
                List.of(true, false),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::local)
                        .get()
                        .filter(b -> !b.name().value().equals("main"))
                        .sorted(java.util.Comparator.comparing(
                                        (Branch b) -> b.name().value())
                                .reversed())
                        .map(Branch::hasRemote));
    }

    @Test
    void remoteBranchesCanBeListedLoggedAndCheckedOutAsABranchThatFollowsThem() throws Exception {
        git("branch", "feature");
        git("checkout", "-q", "feature");
        git("commit", "-q", "--allow-empty", "-m", "On feature");
        git("checkout", "-q", "main");
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "main", "feature");
        git("remote", "set-head", "origin", "main");
        git("branch", "-D", "feature");
        RemoteBranch feature = new RemoteBranch("origin", new BranchName("feature"));

        assertEquals(
                List.of("origin/feature", "origin/main"),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::remote)
                        .get()
                        .map(ref -> ref.branch().shortName())
                        .sorted());
        assertEquals(
                List.of("On feature", "Initial commit"),
                repository
                        .history()
                        .log(new BranchRef.Remote(feature))
                        .get()
                        .map(c -> c.message().summary())
                        .toList());

        repository
                .checkout()
                .newTrackingBranch(new BranchName("feature"), feature)
                .get();

        Branch local = repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .find(b -> b.name().equals(new BranchName("feature")))
                .get();
        assertTrue(local.current());
        assertEquals(Option.of(new BranchRef.Remote(feature)), local.upstream().map(upstream -> upstream.branch()));
    }

    @Test
    void branchesCountTheCommitsNotYetPushedAndNotYetPulled() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "-u", "origin", "main");
        assertEquals(List.of(0, 0), counts("main"));

        git("commit", "-q", "--allow-empty", "-m", "local one");
        git("commit", "-q", "--allow-empty", "-m", "local two");
        assertEquals(List.of(2, 0), counts("main"));

        // Someone else pushes a commit; fetching makes it incoming.
        java.nio.file.Path other = Files.createTempDirectory("other");
        gitIn(other, "clone", "-q", bare.toString(), "clone");
        gitIn(other.resolve("clone"), "commit", "-q", "--allow-empty", "-m", "theirs");
        gitIn(other.resolve("clone"), "push", "-q", "origin", "main");
        git("fetch", "-q", "origin");
        assertEquals(List.of(2, 1), counts("main"));
    }

    @Test
    void branchesWithoutAnUpstreamAreNeitherAheadNorBehind() throws Exception {
        git("branch", "local-only");
        assertEquals(List.of(0, 0), counts("local-only"));
    }

    @Test
    void theDefaultBranchIsTheOneOriginHeadPointsToElseMainOrMaster() throws Exception {
        git("branch", "trunk");
        assertEquals(List.of("main"), defaultBranches());

        git("update-ref", "refs/remotes/origin/trunk", "HEAD");
        git("symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/trunk");
        assertEquals(List.of("trunk"), defaultBranches());
    }

    private List<String> defaultBranches() {
        return repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .filter(Branch::isDefault)
                .map(b -> b.name().value())
                .toList();
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private List<Integer> counts(String branch) {
        return CliGitFixture.counts(repository, branch);
    }
}
