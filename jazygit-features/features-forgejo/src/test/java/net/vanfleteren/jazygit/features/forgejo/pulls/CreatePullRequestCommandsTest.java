package net.vanfleteren.jazygit.features.forgejo.pulls;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FEATURE;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.HOST;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.URL;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoNewPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CheckHead;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FakeForgejo;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import org.junit.jupiter.api.Test;

/**
 * Checking where a branch is pushed to, and creating its pull request on Forgejo (ADR 178).
 */
class CreatePullRequestCommandsTest {

    private static final BranchName BRANCH = new BranchName("feature");
    private static final BranchName MAIN = new BranchName("main");
    private static final RemoteName UPSTREAM = new RemoteName("upstream");
    private static final ForgejoPullRequest CREATED = new ForgejoPullRequest(
            13, "Create it", "feature", Option.of("cvf/nix"), "main", false, "https://code.example.net/cvf/nix/13");

    /**
     * origin is Anna's fork, upstream the project, the home.
     */
    private static final Map<RemoteName, String> FORKED =
            HashMap.of(ORIGIN, "git@code.example.net:anna/nix.git", UPSTREAM, URL);

    private final ConcurrentLinkedQueue<ForgejoNewPullRequest> sent = new ConcurrentLinkedQueue<>();

    private FakeForgejo answering(Either<ForgejoError, ForgejoPullRequest> answer) {
        return FakeForgejo.of(details("Show")).creating(pullRequest -> {
            sent.add(pullRequest);
            return answer;
        });
    }

    private static CreatePullRequest create(RemoteName home, boolean draft) {
        return new CreatePullRequest(home, BRANCH, FEATURE, MAIN, "Create it", "Why", draft);
    }

    private static Option<Phrase> failure(Msg msg) {
        return msg instanceof OperationMsg.Failed(var cmd, var failure) && failure instanceof CreateFailure failed
                ? Option.of(failed.phrase())
                : Option.none();
    }

    @Test
    void aBranchPushedToARemoteOnTheHostOfTheHomeCanHaveOne() {
        assertEquals(
                new CreatePullRequestMsg.HeadChecked(BRANCH, Option.none()),
                new CheckHead(UPSTREAM, BRANCH, FEATURE)
                        .run(answering(Either.right(CREATED)).env(FORKED)));
    }

    @Test
    void aBranchPushedToAnotherHostCannot() {
        RemoteBranch onGitHub = new RemoteBranch(new RemoteName("github"), BRANCH);
        Map<RemoteName, String> urls = HashMap.of(ORIGIN, URL, onGitHub.remote(), "git@github.com:cvf/nix.git");

        assertEquals(
                new CreatePullRequestMsg.HeadChecked(BRANCH, Option.of(new CreateError.Elsewhere(BRANCH, onGitHub))),
                new CheckHead(ORIGIN, BRANCH, onGitHub)
                        .run(answering(Either.right(CREATED)).env(urls)));
    }

    @Test
    void aBranchInTheHomeIsSentByItsNameAndADraftWithThePrefix() {
        CreatePullRequest cmd = create(ORIGIN, true);

        Msg created = cmd.run(answering(Either.right(CREATED)).ahead(HashMap.of(ORIGIN, URL), 2));

        assertEquals(new CreatePullRequestMsg.Created(cmd, CREATED), created);
        ForgejoNewPullRequest pullRequest = List.ofAll(sent).single();
        assertEquals(new ForgejoNewPullRequest("feature", "main", "Create it", "Why", true), pullRequest);
        assertEquals("WIP: Create it", pullRequest.sentTitle());
    }

    @Test
    void aBranchInAForkIsSentWithItsOwner() {
        create(UPSTREAM, false).run(answering(Either.right(CREATED)).ahead(FORKED, 2));

        assertEquals("anna:feature", List.ofAll(sent).single().head());
    }

    @Test
    void aBranchWithNoCommitTheBaseLacksIsNotSent() {
        Msg failed = create(ORIGIN, false).run(answering(Either.right(CREATED)).ahead(HashMap.of(ORIGIN, URL), 0));

        assertEquals(Option.of(new CreateFailure.Empty(BRANCH, MAIN).phrase()), failure(failed));
        assertEquals(List.empty(), List.ofAll(sent));
    }

    @Test
    void oneOpenAlreadyIsNamed() {
        CreatePullRequest cmd = create(ORIGIN, false);
        ForgejoError taken = new ForgejoError.Refused(
                409, "{\"message\":\"pull request already exists for these targets [id: 81, issue_id: 7]\"}");

        assertEquals(
                new CreatePullRequestMsg.AlreadyOpen(cmd, 7),
                cmd.run(answering(Either.left(taken)).ahead(HashMap.of(ORIGIN, URL), 2)));
    }

    @Test
    void withoutATokenItSaysHowToGetOne() {
        ForgejoError locked = new ForgejoError.Refused(401, "{\"message\":\"token is required\"}");

        Msg failed = create(ORIGIN, false).run(answering(Either.left(locked)).ahead(HashMap.of(ORIGIN, URL), 2));

        assertEquals(Option.of(Phrase.of("forgejo.error.create.token", HOST)), failure(failed));
    }

    @Test
    void aForkTheHostDoesNotKnowAsOneSaysItMustBe() {
        ForgejoError missing = new ForgejoError.Refused(404, "{\"message\":\"anna does not have a fork\"}");

        Msg failed = create(UPSTREAM, false).run(answering(Either.left(missing)).ahead(FORKED, 2));

        assertEquals(Option.of(Phrase.of("forgejo.error.create.notFork", "anna/nix", "cvf/nix")), failure(failed));
    }

    @Test
    void otherRefusalsGiveForgejosMessage() {
        ForgejoError same = new ForgejoError.Refused(422, "{\"message\":\"There are no changes\"}");

        Msg failed = create(ORIGIN, false).run(answering(Either.left(same)).ahead(HashMap.of(ORIGIN, URL), 2));

        assertEquals(
                Option.of(Phrase.of("forgejo.error.create.refused", "422", "There are no changes")), failure(failed));
    }
}
