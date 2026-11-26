package net.vanfleteren.jazygit.features.github.browse;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.features.github.GitHubError;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Opening branches on GitHub.
 */
class BrowseUpdateTest {

    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final Branch RENAMED = Fixtures.branch(
            new BranchName("mine"),
            false,
            new Sha("1"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(ORIGIN_FEATURE), 0, 0)));
    private static final Branch WITHOUT_UPSTREAM =
            Fixtures.branch(new BranchName("plain"), false, new Sha("2"), Instant.EPOCH, true, false);
    private static final Branch LOCAL = Fixtures.branch(new BranchName("local"), false, new Sha("3"));
    private static final Branch GONE = Fixtures.branch(
            new BranchName("gone"),
            false,
            new Sha("4"),
            Instant.EPOCH,
            false,
            false,
            new Tracking.Gone(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("gone")))));

    private static Model model() {
        return TestModels.localBranchesRead(loaded(), List.of(RENAMED, WITHOUT_UPSTREAM, LOCAL, GONE))
                .model();
    }

    @Test
    void aBranchIsOpenedByItsNameOnTheRemote() {
        Next next = Update.update(model(), new BrowseMsg.Requested(new BranchName("mine")));

        assertEquals(List.of(new BrowseCmd(ORIGIN_FEATURE)), next.cmds());
        assertEquals(Option.none(), next.model().outcome().error());
    }

    @Test
    void aBranchWithoutUpstreamThatIsOnOriginIsOpenedThere() {
        assertEquals(
                List.of(new BrowseCmd(new RemoteBranch("origin", new BranchName("plain")))),
                Update.update(model(), new BrowseMsg.Requested(new BranchName("plain")))
                        .cmds());
    }

    @Test
    void aBranchOnNoRemoteIsNotOpened() {
        Next local = Update.update(model(), new BrowseMsg.Requested(new BranchName("local")));
        assertEquals(List.empty(), local.cmds());
        assertEquals(
                Option.of(new GitHubError.NotOnRemote(new BranchName("local"))),
                local.model().outcome().error());

        Next gone = Update.update(model(), new BrowseMsg.Requested(new BranchName("gone")));
        assertEquals(List.empty(), gone.cmds());
        assertEquals(
                Option.of(new GitHubError.NotOnRemote(new BranchName("gone"))),
                gone.model().outcome().error());
    }

    @Test
    void aRemoteBranchIsOpened() {
        assertEquals(
                List.of(new BrowseCmd(ORIGIN_FEATURE)),
                Update.update(model(), new BrowseMsg.RemoteRequested(ORIGIN_FEATURE))
                        .cmds());
    }

    @Test
    void aPullRequestOfAnyRepositoryOpensByItsUrl() {
        PullRequestSummary pr = new PullRequestSummary(
                "other/repo",
                new PrNumber(3),
                "Elsewhere",
                "carol",
                new GhBranch("fix"),
                false,
                false,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.EPOCH,
                "https://github.com/other/repo/pull/3");

        assertEquals(
                List.of(new BrowsePullRequestCmd(pr)),
                Update.update(loaded(), new BrowseMsg.PullRequestRequested(pr)).cmds());
    }
}
