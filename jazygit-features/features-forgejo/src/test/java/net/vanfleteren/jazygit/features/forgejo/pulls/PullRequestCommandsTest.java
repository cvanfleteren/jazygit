package net.vanfleteren.jazygit.features.forgejo.pulls;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FEATURE;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.LISTED;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoBranchRules;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FakeForgejo;
import net.vanfleteren.jazygit.kernel.state.Failure;
import org.junit.jupiter.api.Test;

/**
 * Reading the pull requests on Forgejo, and one of them to show it (ADR 176).
 */
class PullRequestCommandsTest {

    private static final ForgejoError DOWN = new ForgejoError.Unreachable("timed out");

    @Test
    void theOpenPullRequestsOfTheHomeAreKnownByTheBranchTheyMergeThoseOfOthersForksLeftOut() {
        ForgejoPullRequest fromFork = new ForgejoPullRequest(
                13, "From a fork", "feature", Option.of("someone/nix"), "main", false, "https://x/13");
        FakeForgejo forgejo = FakeForgejo.of(details("Show")).withOpen(Either.right(List.of(LISTED, fromFork)));

        assertEquals(
                new PullRequestMsg.Listed(ORIGIN, Option.of(HashMap.of(FEATURE, LISTED)), Option.none()),
                new ReadPullRequests(ORIGIN).run(forgejo.env()));
    }

    @Test
    void thoseOfTheProjectAForkWasMadeOfAreKnownByTheBranchOfTheForkTheyMerge() {
        // origin is Anna's fork, upstream the project: her pull request is the project's, its branch in her fork.
        ForgejoPullRequest fromAnna = new ForgejoPullRequest(
                14, "From Anna's fork", "feature", Option.of("Anna/Nix"), "main", false, "https://x/14");
        FakeForgejo forgejo = FakeForgejo.of(details("Show")).withOpen(Either.right(List.of(fromAnna)));

        assertEquals(
                new PullRequestMsg.Listed(
                        new RemoteName("upstream"), Option.of(HashMap.of(FEATURE, fromAnna)), Option.none()),
                new ReadPullRequests(new RemoteName("upstream"))
                        .run(forgejo.env(HashMap.of(
                                ORIGIN,
                                "git@code.example.net:anna/nix.git",
                                new RemoteName("upstream"),
                                "https://code.example.net/cvf/nix.git"))));
    }

    @Test
    void aHomeOverPlainHttpIsToldToGetTheTokenUnencryptedAndOneOverHttpsIsNot() {
        FakeForgejo forgejo = FakeForgejo.of(details("Show"));

        assertEquals(
                new PullRequestMsg.PlainHttpChecked(Option.of("http://code.example.net")),
                new CheckPlainHttp(ORIGIN).run(forgejo.env(HashMap.of(ORIGIN, "http://code.example.net/cvf/nix.git"))));
        assertEquals(new PullRequestMsg.PlainHttpChecked(Option.none()), new CheckPlainHttp(ORIGIN).run(forgejo.env()));
    }

    @Test
    void withoutAHomeOnForgejoNoneAreRead() {
        FakeForgejo forgejo = FakeForgejo.of(details("Show")).withOpen(Either.left(DOWN));

        assertEquals(
                new PullRequestMsg.Listed(new RemoteName("github"), Option.none(), Option.none()),
                new ReadPullRequests(new RemoteName("github"))
                        .run(forgejo.env(HashMap.of(new RemoteName("github"), "git@github.com:cvf/nix.git"))));
    }

    @Test
    void whenTheyCannotBeReadItSaysWhy() {
        FakeForgejo forgejo = FakeForgejo.of(details("Show")).withOpen(Either.left(DOWN));

        assertEquals(
                new PullRequestMsg.Listed(ORIGIN, Option.none(), Option.of(new ForgejoFailure.Asked(DOWN))),
                new ReadPullRequests(ORIGIN).run(forgejo.env()));
    }

    @Test
    void aPullRequestIsReadWithWhereItsReviewStandsHowItsBuildsWentAndHowItMayBeMerged() {
        FakeForgejo forgejo = FakeForgejo.of(details("Show"));

        assertEquals(
                new PullRequestMsg.Read(
                        3,
                        Either.right(new ShownPullRequest(
                                details("Show"),
                                Option.of(ForgejoReview.Verdict.APPROVED),
                                forgejo.status().get(),
                                Option.of(PullRequestFixtures.SETTINGS),
                                Option.of(ForgejoBranchRules.NONE),
                                1,
                                Option.none()))),
                new LoadPullRequest(ORIGIN, 12, 3).run(forgejo.env()));
    }

    @Test
    void reviewsBuildsOrSettingsThatCannotBeReadLeaveThePullRequestShownWithoutThem() {
        FakeForgejo forgejo = FakeForgejo.failing(Either.right(details("Show")), Either.left(DOWN), DOWN);

        assertEquals(
                new PullRequestMsg.Read(
                        3, Either.right(new ShownPullRequest(details("Show"), Option.none(), CommitStatus.NONE))),
                new LoadPullRequest(ORIGIN, 12, 3).run(forgejo.env()));
    }

    @Test
    void aPullRequestThatCannotBeReadOrAHomeNoLongerOnForgejoFails() {
        FakeForgejo down = FakeForgejo.failing(Either.left(DOWN), Either.right(List.empty()), DOWN);
        assertEquals(
                new PullRequestMsg.Read(3, Either.<Failure, ShownPullRequest>left(new ForgejoFailure.Asked(DOWN))),
                new LoadPullRequest(ORIGIN, 12, 3).run(down.env()));

        assertEquals(
                new PullRequestMsg.Read(3, Either.<Failure, ShownPullRequest>left(new ForgejoFailure.NoHome())),
                new LoadPullRequest(ORIGIN, 12, 3)
                        .run(FakeForgejo.of(details("Show")).env(HashMap.of(ORIGIN, "git@github.com:cvf/nix.git"))));
    }
}
