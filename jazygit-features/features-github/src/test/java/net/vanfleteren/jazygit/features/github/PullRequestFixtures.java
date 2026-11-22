package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.kernel.state.TestModels.*;

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
import net.vanfleteren.jazygit.features.github.prs.PullRequestReads;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;

/**
 * The branches and pull requests the pull request update tests share.
 */
public final class PullRequestFixtures {

    /**
     * The id of the last read of the details of a pull request given out in {@code model}: the one a dialog that just
     * started a read waits for (ADR 34).
     */
    public static long lastRead(Model model) {
        return model.part(GitHubState.PART).nextRead() - 1;
    }

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

    public static final BranchName FEATURE = PUSHED.name();

    static final PullRequest FEATURE_PR =
            new PullRequest(new PrNumber(42), new GhBranch("feature"), false, Checks.PASSING, Review.APPROVED);

    private PullRequestFixtures() {}

    /**
     * What the screen's update hands the branches once the open pull requests {@code pullRequests} of the repository
     * were read (ADR 168).
     */
    public static Msg listed(List<PullRequest> pullRequests) {
        return new Listed(pullRequests);
    }

    /**
     * What the screen's update hands the branches once the open pull requests could not be read for {@code failure}.
     */
    public static Msg listFailed(Failure failure) {
        return new ListFailed(failure);
    }

    public static Model withPr() {
        return Update.update(model(), PullRequestFixtures.listed(List.of(FEATURE_PR)))
                .model();
    }

    static Env env() {
        return new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of());
    }

    /**
     * The sample model, with GitHub's feature answering what happens in the kernel (ADR 158): the one place the
     * tests of GitHub's pull requests give their model the reactions.
     */
    public static Model loadedWithTheFeature() {
        return loaded().withReactions(new Reactions(GitHubFeature.FEATURE.reactions()));
    }

    /**
     * The branches, and no pull request from any of them; GitHub's feature answers what happens in the kernel.
     */
    static Model model() {
        // The default branch is listed last, to see it offered first.
        Model branches = localBranchesRead(loadedWithTheFeature(), List.of(RELEASE, PUSHED, UNPUSHED, AHEAD, MAIN))
                .model();
        return Update.update(branches, PullRequestFixtures.listed(List.empty())).model();
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

    private record Listed(List<PullRequest> pullRequests) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return PullRequestReads.loaded(model, pullRequests, "owner/repo");
        }
    }

    private record ListFailed(Failure failure) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return PullRequestReads.failed(model, failure);
        }
    }
}
