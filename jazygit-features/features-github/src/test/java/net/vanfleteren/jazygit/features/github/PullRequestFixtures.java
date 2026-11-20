package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.lastRead;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.github.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;

/**
 * The branches and pull requests the pull request update tests share.
 */
final class PullRequestFixtures {

    static final Branch MAIN = Fixtures.branch(
            new BranchName("main"),
            true,
            new Sha("1"),
            Instant.EPOCH,
            true,
            true,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))), 0, 0)));

    static final Branch RELEASE = Fixtures.branch(
            new BranchName("release"),
            false,
            new Sha("2"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("release"))), 0, 0)));

    static final Branch PUSHED = Fixtures.branch(
            new BranchName("feature"),
            false,
            new Sha("3"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))), 0, 0)));

    static final Branch UNPUSHED = Fixtures.branch(new BranchName("local"), false, new Sha("4"));

    static final Branch AHEAD = Fixtures.branch(
            new BranchName("ahead"),
            false,
            new Sha("5"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("ahead"))), 2, 0)));

    static final BranchName FEATURE = PUSHED.name();

    static final PullRequest FEATURE_PR =
            new PullRequest(new PrNumber(42), new GhBranch("feature"), false, Checks.PASSING, Review.APPROVED);

    private PullRequestFixtures() {}

    static Model withPr() {
        return Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();
    }

    static Env env() {
        return new Env(StubGit.of(), StubGitHub.of());
    }

    /**
     * The branches, and no pull request from any of them.
     */
    static Model model() {
        // The default branch is listed last, to see it offered first.
        Model branches = localBranchesRead(loaded(), List.of(RELEASE, PUSHED, UNPUSHED, AHEAD, MAIN))
                .model();
        return Update.update(branches, new PullRequestsMsg.Loaded(List.empty())).model();
    }

    static BranchRef remote(String branch) {
        return new BranchRef.Remote(new RemoteBranch("origin", new BranchName(branch)));
    }

    /**
     * {@code details} with one check that runs, of a commit status, which is not timed.
     */
    static PullRequestDetails withARunningCheck(PullRequestDetails details) {
        return details.withChecks(List.of(new PullRequestDetails.Check("ci/status", Checks.PENDING)));
    }

    /**
     * The pull request of the feature branch shown, with its details read.
     */
    static Model showingDetails() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        return Update.update(shown, new LoadPullRequestDetails(new PrNumber(42), lastRead(shown)).run(env()))
                .model();
    }

    static PullRequestSummary summary(String repository) {
        return summary(repository, 9);
    }

    /**
     * Pull request {@code number} of {@code repository}, from a fork, listed without its commits.
     */
    static PullRequestSummary summary(String repository, int number) {
        return new PullRequestSummary(
                repository,
                new PrNumber(number),
                "From a fork",
                "bob",
                new GhBranch("patch-1"),
                false,
                true,
                Checks.NONE,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/" + repository + "/pull/" + number);
    }
}
