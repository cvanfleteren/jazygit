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
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checking out a branch of a real repository, making a new one, and resolving what the user typed.
 */
class CliCheckoutBranchTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void checkoutSwitchesBranchAndCarriesOverLocalChanges() throws Exception {
        git("branch", "feature");
        Files.writeString(repo.resolve("new.txt"), "new\n");

        repository.checkout().branch(new BranchName("feature")).get();

        assertEquals(
                Option.of(new BranchName("feature")),
                repository.status().read().get().head().branchName());
        assertEquals(
                List.of(Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)),
                repository.status().read().get().files());
    }

    @Test
    void checkoutFailsWithGitsExplanationWhenLocalChangesWouldBeOverwritten() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "changed on feature\n");
        git("commit", "-q", "-am", "Change README");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("README.md"), "local change\n");

        GitError error = repository
                .checkout()
                .branch(new BranchName("feature"))
                .getLeft()
                .error();

        assertTrue(
                error instanceof GitError.LocalChanges(String explanation)
                        && explanation.contains("would be overwritten"),
                error.toString());
        assertEquals(
                Option.of(new BranchName("main")),
                repository.status().read().get().head().branchName());
    }

    @Test
    void checkoutNewBranchStartsAtTheGivenBranchAndSwitchesToIt() throws Exception {
        git("checkout", "-q", "-b", "other");
        Files.writeString(repo.resolve("other.txt"), "other\n");
        git("add", "other.txt");
        git("commit", "-q", "-m", "On other");
        git("checkout", "-q", "main");

        repository
                .checkout()
                .newBranch(
                        new BranchName("topic"),
                        new BranchRef.Local(new BranchName("other")),
                        Option.of(new BranchName("other")))
                .get();

        assertEquals(
                Option.of(new BranchName("topic")),
                repository.status().read().get().head().branchName());
        assertTrue(Files.exists(repo.resolve("other.txt")));
        assertEquals(
                Option.of(new BranchName("other")),
                repository.branches().parent(new BranchName("topic")).get());
    }

    @Test
    void aBranchMadeFromARemoteBranchFollowsItAsGitIsConfiguredTo() throws Exception {
        git("remote", "add", "origin", repo.toString());
        git("update-ref", "refs/remotes/origin/develop", "HEAD");
        Revision develop = new RemoteBranch("origin", new BranchName("develop")).tracking();

        git("config", "branch.autoSetupMerge", "true");
        repository
                .checkout()
                .newBranch(new BranchName("follows"), develop, Option.none())
                .get();
        git("config", "branch.autoSetupMerge", "false");
        repository
                .checkout()
                .newBranch(new BranchName("alone"), develop, Option.none())
                .get();

        assertTrue(gitSucceedsIn(repo, "config", "--get", "branch.follows.merge"));
        assertFalse(gitSucceedsIn(repo, "config", "--get", "branch.alone.merge"));
    }

    @Test
    void whatTheUserTypedIsResolvedToTheCommitItNamesAndNeverReadAsAnOption() throws Exception {
        git("tag", "v1");
        Sha head = repository.checkout().resolve("HEAD").get().get();

        assertEquals(Option.of(head), repository.checkout().resolve("v1").get());
        assertEquals(Option.none(), repository.checkout().resolve("--all").get());
        assertEquals(Option.none(), repository.checkout().resolve("nowhere").get());
    }

    @Test
    void aBranchMadeWithoutAParentHasNoneRecorded() throws Exception {
        repository
                .checkout()
                .newBranch(new BranchName("topic"), new BranchRef.Local(new BranchName("main")), Option.none())
                .get();

        assertEquals(
                Option.none(),
                repository.branches().parent(new BranchName("topic")).get());
    }

    @Test
    void failingToRecordTheParentStillMakesTheBranch() throws Exception {
        // git config cannot write the configuration while another writer holds its lock.
        Files.writeString(repo.resolve(".git/config.lock"), "");

        var made = repository
                .checkout()
                .newBranch(
                        new BranchName("topic"),
                        new BranchRef.Local(new BranchName("main")),
                        Option.of(new BranchName("main")));

        assertTrue(made.isRight());
        assertEquals(2, made.get().size());
        assertEquals(
                Option.of(new BranchName("topic")),
                repository.status().read().get().head().branchName());
        assertEquals(
                Option.none(),
                repository.branches().parent(new BranchName("topic")).get());
    }

    @Test
    void checkoutNewBranchFailsWhenTheNameIsTaken() throws Exception {
        git("branch", "topic");

        assertTrue(repository
                .checkout()
                .newBranch(new BranchName("topic"), new BranchRef.Local(new BranchName("main")), Option.none())
                .isLeft());
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }
}
