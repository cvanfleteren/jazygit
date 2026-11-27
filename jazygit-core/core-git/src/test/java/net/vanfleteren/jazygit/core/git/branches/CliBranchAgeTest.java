package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The order of the branches of a real repository, and the time of their last activity it follows.
 */
class CliBranchAgeTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void branchesListTheCurrentOneFirstThenTheMostRecentlyCommittedTo() throws Exception {
        commitAt("main", "2020-01-02T00:00:00Z");
        for (String branch : List.of("old", "newest", "middle")) {
            gitAt("2020-01-02T12:00:00Z", "branch", branch, "main");
        }
        commitAt("old", "2020-01-03T00:00:00Z");
        commitAt("middle", "2020-01-05T00:00:00Z");
        commitAt("newest", "2020-01-09T00:00:00Z");

        assertEquals(
                List.of("main", "newest", "middle", "old"),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::local)
                        .get()
                        .map(b -> b.name().value())
                        .toList());

        git("checkout", "-q", "old");
        assertEquals(
                List.of("old", "newest", "middle", "main"),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::local)
                        .get()
                        .map(b -> b.name().value())
                        .toList());
    }

    @Test
    void aNewBranchIsAsOldAsWhenItWasMadeUntilItIsCommittedTo() throws Exception {
        commitAt("main", "2020-01-02T00:00:00Z");
        gitAt("2020-01-02T12:00:00Z", "branch", "old", "main");
        commitAt("old", "2020-01-03T00:00:00Z");
        gitAt("2020-01-04T00:00:00Z", "branch", "fresh", "main");

        List<Branch> branches =
                repository.branches().all().map(AllBranches::local).get();
        assertEquals(
                List.of(
                        Tuple.of("main", Instant.parse("2020-01-02T00:00:00Z")),
                        Tuple.of("fresh", Instant.parse("2020-01-04T00:00:00Z")),
                        Tuple.of("old", Instant.parse("2020-01-03T00:00:00Z"))),
                branches.map(b -> Tuple.of(b.name().value(), b.lastActivity())));

        commitAt("fresh", "2020-01-05T00:00:00Z");
        assertEquals(
                Instant.parse("2020-01-05T00:00:00Z"),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::local)
                        .get()
                        .find(b -> b.name().value().equals("fresh"))
                        .get()
                        .lastActivity());
    }

    @Test
    void aBranchCheckedOutFromARemoteIsAsOldAsItsTipButOneStartedFromAnotherIsNew() throws Exception {
        commitAt("main", "2020-01-02T00:00:00Z");
        gitAt("2020-01-02T12:00:00Z", "branch", "colleague", "main");
        commitAt("colleague", "2020-01-03T00:00:00Z");
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("push", "-q", "origin", "main", "colleague");
        git("branch", "-D", "colleague");
        git("fetch", "-q", "origin");

        repository
                .checkout()
                .newTrackingBranch(new BranchName("colleague"), new RemoteBranch("origin", new BranchName("colleague")))
                .get();
        gitAt("2020-01-04T00:00:00Z", "checkout", "-q", "-b", "feature", "origin/main");

        Map<String, Branch> branches = repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .toMap(b -> Tuple.of(b.name().value(), b));
        assertEquals(
                Instant.parse("2020-01-03T00:00:00Z"),
                branches.get("colleague").get().lastActivity());
        assertEquals(
                Option.of(new RemoteBranch("origin", new BranchName("main"))),
                branches.get("feature").get().remoteUpstream());
        assertEquals(
                Instant.parse("2020-01-04T00:00:00Z"),
                branches.get("feature").get().lastActivity());
    }

    private void commitAt(String branch, String date) throws Exception {
        git("checkout", "-q", branch);
        gitAt(date, "commit", "-q", "--allow-empty", "-m", "at " + date);
        git("checkout", "-q", "main");
    }

    /**
     * Runs git with {@code date} as the time of the commits it makes and of the reflog entries it writes.
     */
    private void gitAt(String date, String... args) throws Exception {
        gitWithEnvironment("GIT_COMMITTER_DATE", date, args);
    }

    @Test
    void branchesCarryTheTimeOfTheirLastCommit() {
        assertEquals(
                commits().get().get(0).authorTime().getEpochSecond(),
                repository
                        .branches()
                        .all()
                        .map(AllBranches::local)
                        .get()
                        .get(0)
                        .tipTime()
                        .getEpochSecond());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }

    private Either<GitError, List<Commit>> commits() {
        return CliGitFixture.commits(repository);
    }

    private void gitWithEnvironment(String variable, String value, String... args) throws Exception {
        CliGitFixture.gitWithEnvironment(repo, variable, value, args);
    }
}
