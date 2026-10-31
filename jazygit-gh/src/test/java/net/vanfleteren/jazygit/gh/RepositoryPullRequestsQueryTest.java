package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.RAN_SUITES;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.STACKED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.RepositoryPullRequests;
import org.junit.jupiter.api.Test;

/**
 * The open pull requests of the repository, with who they were read as and which was opened last.
 */
class RepositoryPullRequestsQueryTest {

    private static final String NO_SUITES = "\"checkSuites\": { \"nodes\": [] }";
    private static final String LINE_SEPARATOR = Character.toString(0x2028);

    private static Either<GhError.UnexpectedOutput, RepositoryPullRequests> read(
            String viewer, String latest, int total, String nodes) {
        return GhProcess.decoded("""
                [ { "data": { "viewer": %s, "repository": { "nameWithOwner": "owner/repo",
                  "latest": { "nodes": [ %s ] },
                  "pullRequests": { "totalCount": %d, "nodes": [ %s ] } } } } ]""".formatted(viewer, latest, total, nodes), RepositoryPullRequestsQuery.DECODER);
    }

    @Test
    void theOpenPullRequestsOfTheRepositoryWithHowManyThereAre() {
        assertEquals(
                Either.right(new RepositoryPullRequests(
                        "owner/repo",
                        new PullRequestList(
                                List.of(new PullRequestSummary(
                                        "owner/repo",
                                        7,
                                        "WIP",
                                        "",
                                        "patch-1",
                                        true,
                                        true,
                                        Checks.NONE,
                                        Review.NONE,
                                        Instant.parse("2026-10-15T08:00:00Z"),
                                        "https://github.com/owner/repo/pull/7")),
                                1),
                        Option.of("bob"),
                        7)),
                read("{ \"login\": \"bob\" }", "{ \"number\": 7 }", 1, """
                        { "number": 7, "title": "WIP", "url": "https://github.com/owner/repo/pull/7",
                          "updatedAt": "2026-10-15T08:00:00Z", "isDraft": true, "isCrossRepository": true,
                          "headRefName": "patch-1", "author": null, "repository": { "nameWithOwner": "owner/repo" },
                          "reviewDecision": null, "stackEntry": null, "stack": null,
                          "commits": { "nodes": [ { "commit": { "statusCheckRollup": null, %s } } ] } }""".formatted(NO_SUITES)));
    }

    @Test
    void whoTheyWereReadAsIsNotKnownWhenGitHubDoesNotSay() {
        assertEquals(Option.none(), read("{ \"login\": \"\" }", "", 0, "").get().viewer());
        assertEquals(Option.none(), read("{ \"login\": null }", "", 0, "").get().viewer());
    }

    @Test
    void withoutAPullRequestNoneWasOpenedLast() {
        assertEquals(0, read("{ \"login\": \"bob\" }", "", 0, "").get().latest());
        assertEquals(
                142,
                read("{ \"login\": \"bob\" }", "{ \"number\": 142 }", 0, "")
                        .get()
                        .latest());
    }

    @Test
    void aStackedPullRequestInTheListSaysWhereItSitsInItsStack() {
        assertEquals(
                Option.of(new PullRequestStack(
                        96,
                        2,
                        2,
                        List.of(
                                new PullRequestStack.Entry(1, 94, State.MERGED),
                                new PullRequestStack.Entry(2, 95, State.OPEN)))),
                read("{ \"login\": \"bob\" }", "", 1, summary(95, "null", NO_SUITES, STACKED))
                        .get()
                        .open()
                        .pullRequests()
                        .head()
                        .stack());
    }

    @Test
    void aPullRequestWhoseWorkflowWaitsToStartHasPendingChecksInTheList() {
        assertEquals(
                List.of(Checks.PENDING, Checks.FAILING, Checks.NONE),
                read(
                                "{ \"login\": \"cvf\" }",
                                "{ \"number\": 3 }",
                                3,
                                summary(1, "{ \"state\": \"SUCCESS\" }", WAITING_SUITES) + ", "
                                        + summary(2, "{ \"state\": \"FAILURE\" }", WAITING_SUITES) + ", "
                                        + summary(3, "null", RAN_SUITES))
                        .get()
                        .open()
                        .pullRequests()
                        .map(PullRequestSummary::checks));
    }

    /**
     * The commits pull request 95 in the list is said to be at, as the branches have them, with the fields of
     * {@code oids} after its head branch.
     */
    private static Option<PullRequest.Commits> commits(String oids) {
        return read(
                        "{ \"login\": \"bob\" }",
                        "",
                        1,
                        summary(95, "null", NO_SUITES)
                                .replace("\"headRefName\": \"b95\"", "\"headRefName\": \"b95\"" + oids))
                .get()
                .ofBranches()
                .head()
                .commits();
    }

    @Test
    void theBranchesHaveTheCommitsTheirPullRequestIsDiffedBetween() {
        Option<PullRequest.Commits> commits = commits(", \"headRefOid\": \"aaa\", \"baseRefOid\": \"bbb\"");

        assertEquals(
                Option.of(new PullRequest.Commits(
                        "owner/repo",
                        "https://github.com/owner/repo/pull/95",
                        new GhCommit("bbb"),
                        new GhCommit("aaa"))),
                commits);
        assertEquals("github.com", commits.get().host());
    }

    @Test
    void aPullRequestWhoseBranchIsGoneOrWhoseCommitsAreNotSaidHasNone() {
        assertEquals(Option.none(), commits(", \"headRefOid\": null, \"baseRefOid\": \"bbb\""));
        assertEquals(Option.none(), commits(""));
    }

    @Test
    void aTitleKeepsWhatWouldSplitALine() {
        String title = "a\fb" + LINE_SEPARATOR + "c\u0085d\u000be\tf";

        assertEquals(
                title,
                read(
                                "{ \"login\": \"bob\" }",
                                "",
                                1,
                                summary(95, "null", NO_SUITES)
                                        .replace(
                                                "\"title\": \"t\"", "\"title\": \"a\\fb\\u2028c\\u0085d\\u000be\\tf\""))
                        .get()
                        .open()
                        .pullRequests()
                        .head()
                        .title());
    }

    @Test
    void aListWhoseCountIsNoNumberIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput(
                        "[0].data.repository.pullRequests.totalCount: expected a number, got a string")),
                GhProcess.decoded("""
                        [ { "data": { "viewer": { "login": "bob" }, "repository": { "nameWithOwner": "owner/repo",
                          "latest": { "nodes": [] }, "pullRequests": { "totalCount": "many", "nodes": [] } } } } ]""", RepositoryPullRequestsQuery.DECODER));
    }
}
