package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.CliGitFixture.gitIn;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import net.vanfleteren.jazygit.core.git.CliGitFixture;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Setting and unsetting the upstream of a branch of a real repository.
 */
class CliUpstreamTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);

    @TempDir
    Path remote;

    @TempDir
    Path repo;

    private Git repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = CliGitFixture.init(repo);
        Path bare = remote.resolve("remote.git");
        git("init", "-q", "--bare", "-b", "main", bare.toString());
        git("remote", "add", "origin", bare.toString());
        git("branch", "feature");
        git("push", "-q", "origin", "feature", "main");
        git("fetch", "-q", "origin");
    }

    @Test
    void settingTheUpstreamMakesTheBranchFollowIt() throws Exception {
        assertEquals(
                List.of("git branch --set-upstream-to=refs/remotes/origin/feature -- feature"),
                repository.branches().setUpstream(FEATURE, ORIGIN_FEATURE).get().map(command -> command.line()));

        assertEquals(Option.of(new BranchRef.Remote(ORIGIN_FEATURE)), upstreamOf(FEATURE));
    }

    @Test
    void settingItMovesABranchOffTheOneItTracked() throws Exception {
        git("branch", "--track", "topic", "origin/main");
        assertEquals(
                Option.of(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main")))),
                upstreamOf(new BranchName("topic")));

        repository
                .branches()
                .setUpstream(new BranchName("topic"), ORIGIN_FEATURE)
                .get();

        assertEquals(Option.of(new BranchRef.Remote(ORIGIN_FEATURE)), upstreamOf(new BranchName("topic")));
    }

    @Test
    void aRemoteBranchThatIsNotThereIsRefusedAndNothingChanges() throws Exception {
        git("branch", "--track", "topic", "origin/main");

        assertTrue(repository
                .branches()
                .setUpstream(new BranchName("topic"), new RemoteBranch("origin", new BranchName("missing")))
                .isLeft());

        assertEquals(
                Option.of(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main")))),
                upstreamOf(new BranchName("topic")));
    }

    @Test
    void unsettingTheUpstreamMakesTheBranchFollowNothing() throws Exception {
        git("branch", "--set-upstream-to=origin/feature", "feature");

        assertEquals(
                List.of("git branch --unset-upstream -- feature"),
                repository.branches().unsetUpstream(FEATURE).get().map(command -> command.line()));

        assertEquals(Option.none(), upstreamOf(FEATURE));
    }

    @Test
    void unsettingTheUpstreamOfABranchWithoutOneIsRefused() {
        assertTrue(repository.branches().unsetUpstream(FEATURE).isLeft());
    }

    @Test
    void aBranchWhoseUpstreamWasDeletedCanLoseIt() throws Exception {
        git("branch", "--set-upstream-to=origin/feature", "feature");
        git("push", "-q", "origin", "--delete", "feature");
        git("fetch", "-q", "--prune", "origin");

        repository.branches().unsetUpstream(FEATURE).get();

        assertEquals(Option.none(), upstreamOf(FEATURE));
    }

    private Option<BranchRef> upstreamOf(BranchName name) {
        return repository
                .branches()
                .all()
                .map(AllBranches::local)
                .get()
                .find(branch -> branch.name().equals(name))
                .flatMap(Branch::upstream)
                .map(Upstream::branch);
    }

    private String git(String... args) throws IOException, InterruptedException, TimeoutException {
        return gitIn(repo, args);
    }
}
