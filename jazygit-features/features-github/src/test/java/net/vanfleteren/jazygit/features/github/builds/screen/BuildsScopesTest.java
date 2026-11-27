package net.vanfleteren.jazygit.features.github.builds.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.github.PullRequestFixtures;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import org.junit.jupiter.api.Test;

/**
 * What the builds screen hands the keys of a failed build (ADR 161, 212): the run, of the local branch that follows the
 * branch it ran for, with its pull request.
 */
class BuildsScopesTest {

    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");
    private static final BranchName FX = new BranchName("fx");
    private static final BranchName FEATURE_X = new BranchName("feature-x");

    private static Build run(String conclusion, BranchName branch) {
        return new Build(
                "Build",
                "pull_request",
                401,
                1,
                new Sha("abcdef1"),
                "Commit",
                T,
                List.of(new Build.Job("verify", conclusion, Option.of(T), Option.of(T.plusSeconds(60)))),
                Option.none(),
                new Build.Origin(Option.of("W_build"), Option.of(branch), Option.of(new PrNumber(42))));
    }

    /**
     * {@code fx}, which follows {@code origin/feature-x}, the branch of the open pull request #42.
     */
    private static Model model() {
        Branch fx = Fixtures.branch(
                FX,
                true,
                new Sha("f00"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", FEATURE_X)), 0, 0)));
        Model branches = TestModels.localBranchesRead(PullRequestFixtures.loadedWithTheFeature(), List.of(fx))
                .model();
        return Update.update(
                        branches,
                        PullRequestFixtures.listed(List.of(new PullRequest(
                                new PrNumber(42), new ForgeBranch("feature-x"), false, Checks.FAILING, Review.NONE))))
                .model();
    }

    @Test
    void aFailedRunIsOfTheLocalBranchThatFollowsTheOneItRanForWithItsPullRequest() {
        Option<FailedBuild> failed = BuildsScopes.failed(model(), Option.of(run("FAILURE", FEATURE_X)));

        assertEquals(Option.of(FX), failed.map(FailedBuild::branch));
        assertEquals(
                Option.of(new PrNumber(42)),
                failed.flatMap(FailedBuild::changeRequest).map(FailedBuild.ChangeRequest::number));
    }

    @Test
    void aFailedRunOfABranchNoLocalOneFollowsIsHandedNothing() {
        assertEquals(
                Option.none(),
                BuildsScopes.failed(model(), Option.of(run("FAILURE", new BranchName("someone-elses")))));
    }

    @Test
    void aRunThatPassedIsHandedNothing() {
        assertEquals(Option.none(), BuildsScopes.failed(model(), Option.of(run("SUCCESS", FEATURE_X))));
    }
}
