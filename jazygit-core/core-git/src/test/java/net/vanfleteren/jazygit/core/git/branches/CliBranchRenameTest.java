package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renaming a branch of a real repository.
 */
class CliBranchRenameTest {

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
    }

    @Test
    void renameBranchKeepsItsUpstream() throws Exception {
        java.nio.file.Path bare = Files.createTempDirectory("remote").resolve("remote.git");
        git("init", "-q", "--bare", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("branch", "feature");
        git("push", "-q", "--set-upstream", "origin", "feature");

        assertEquals(
                List.of("git branch --move -- feature topic"),
                repository
                        .branches()
                        .rename(new BranchName("feature"), new BranchName("topic"))
                        .get()
                        .map(command -> command.line()));

        Branch topic = repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .find(b -> b.name().equals(new BranchName("topic")))
                .get();
        assertEquals(
                Option.of(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature")))),
                topic.upstream().map(upstream -> upstream.branch()));
        assertTrue(repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .find(b -> b.name().equals(new BranchName("feature")))
                .isEmpty());
    }

    @Test
    void renameBranchFailsWhenTheNameIsTaken() throws Exception {
        git("branch", "feature");
        git("branch", "topic");

        assertTrue(repository
                .branches()
                .rename(new BranchName("feature"), new BranchName("topic"))
                .isLeft());
        assertTrue(repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .exists(b -> b.name().equals(new BranchName("feature"))));
    }

    @Test
    void renameBranchToAnOptionLookalikeLeavesBothBranchesAsTheyWere() throws Exception {
        git("branch", "feature");
        git("checkout", "-q", "feature");
        String main = git("rev-parse", "main");

        assertTrue(repository
                .branches()
                .rename(new BranchName("main"), new BranchName("--force"))
                .isLeft());

        assertEquals(main, git("rev-parse", "main"));
        assertTrue(repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .exists(b -> b.name().equals(new BranchName("feature"))));
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }
}
