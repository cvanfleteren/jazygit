package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import org.junit.jupiter.api.Test;

/**
 * Merging through REST's {@code merge-async}, and how a merge is doing from what it answers.
 */
class MergeQueryTest {

    private static final String LINE_SEPARATOR = Character.toString(0x2028);

    private static Either<GhError.UnexpectedOutput, MergeStatus> status(String json) {
        return GhProcess.decoded(json, MergeQuery.DECODER);
    }

    @Test
    void aMergeIsAskedOfTheRestApiWithItsMethodAndOnlyAtTheHeadThatWasRead() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "--method",
                        "PUT",
                        "repos/{owner}/{repo}/pulls/95/merge-async",
                        "-f",
                        "merge_method=squash",
                        "-f",
                        "sha=abc123"),
                MergeQuery.mergeCommand(95, Option.of(new GhCommit("abc123")), MergeMethod.SQUASH)
                        .executed()
                        .commandLine());
        assertFalse(MergeQuery.mergeCommand(95, Option.none(), MergeMethod.REBASE)
                .executed()
                .commandLine()
                .exists(arg -> arg.startsWith("sha=")));
        assertEquals(
                List.of("gh", "api", "repos/{owner}/{repo}/pulls/95/merge-async/u-1"),
                MergeQuery.statusCommand(95, "u-1").executed().commandLine());
    }

    @Test
    void howAMergeIsDoing() {
        assertEquals(
                Either.right(new MergeStatus.Pending("u-1")),
                status("{\"status\": \"pending\", \"details\": {\"uuid\": \"u-1\"}}"));
        assertEquals(
                Either.right(new MergeStatus.Merged()),
                status("{\"status\": \"merged\", \"details\": {\"message\": \"Merged\"}}"));
        assertEquals(Either.right(new MergeStatus.Enqueued()), status("{\"status\": \"enqueued\"}"));
        assertEquals(Either.right(new MergeStatus.Failed("Required status check \"build\" is failing")), status("""
                        {"status": "failed", "details": {"message": "Required status check \\"build\\" is failing"}}"""));
    }

    @Test
    void aFailedMergeKeepsItsMessageAsItIs() {
        assertEquals(
                Either.right(new MergeStatus.Failed("bad" + LINE_SEPARATOR + "\nworse")),
                status("{\"status\": \"failed\", \"details\": {\"message\": \"bad\\u2028\\nworse\"}}"));
    }

    @Test
    void aFailedMergeWithoutAMessageFailedForNoReasonGiven() {
        assertEquals(
                Either.right(new MergeStatus.Failed("")),
                status("{\"status\": \"failed\", \"details\": {\"message\": null}}"));
        assertEquals(Either.right(new MergeStatus.Failed("")), status("{\"status\": \"failed\", \"details\": null}"));
        assertEquals(Either.right(new MergeStatus.Failed("")), status("{\"status\": \"failed\", \"details\": {}}"));
        assertEquals(Either.right(new MergeStatus.Failed("")), status("{\"status\": \"failed\"}"));
    }

    @Test
    void aPendingMergeWithoutItsIdIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("details.uuid: expected the id of the merge")),
                status("{\"status\": \"pending\", \"details\": {\"uuid\": \"\"}}"));
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("details: missing")), status("{\"status\": \"pending\"}"));
    }

    @Test
    void aStatusThatIsNotKnownIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("expected pending, merged, enqueued or failed, got \"done\"")),
                status("{\"status\": \"done\"}"));
        assertEquals(Either.left(new GhError.UnexpectedOutput("expected JSON, got nothing")), status(""));
    }
}
