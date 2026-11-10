package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.Verdict;
import org.junit.jupiter.api.Test;

/**
 * The command that approves a pull request or asks for changes (ADR 99).
 */
class ReviewMutationTest {

    @Test
    void aReviewIsAddedToThePullRequestByItsNodeIdWithItsVerdictAndText() {
        List<String> args = ReviewMutation.submit("PR_1", Verdict.REQUEST_CHANGES, "@fix the test")
                .executed()
                .commandLine();

        assertEquals(List.of("gh", "api", "graphql", "-f"), args.take(4));
        assertTrue(args.get(4).contains("addPullRequestReview"), args.get(4));
        assertEquals(
                List.of("-f", "pullRequest=PR_1", "-f", "event=REQUEST_CHANGES", "-f", "body=@fix the test"),
                args.drop(5));
    }

    @Test
    void anApprovalMayHaveNoText() {
        assertEquals(
                List.of("-f", "event=APPROVE", "-f", "body="),
                ReviewMutation.submit("PR_1", Verdict.APPROVE, "")
                        .executed()
                        .commandLine()
                        .drop(7));
    }

    @Test
    void onlyAskingForChangesNeedsAText() {
        assertTrue(Verdict.REQUEST_CHANGES.needsText());
        assertFalse(Verdict.APPROVE.needsText());
    }
}
