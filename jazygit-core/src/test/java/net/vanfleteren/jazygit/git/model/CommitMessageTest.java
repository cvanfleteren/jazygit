package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Reading a commit message as git stores it.
 */
class CommitMessageTest {

    @Test
    void theFirstLineIsTheSummaryAndWhatFollowsTheBlankLineTheDescription() {
        assertEquals(
                new CommitMessage("Fix it", "Because.\n\nReally."),
                CommitMessage.parse("Fix it\n\nBecause.\n\nReally.\n"));
    }

    @Test
    void aMessageWithoutDescriptionIsOnlyASummary() {
        assertEquals(new CommitMessage("Fix it", ""), CommitMessage.parse("\n  Fix it  \n\n"));
    }

    @Test
    void anEmptyMessageHasNoSummary() {
        assertEquals(new CommitMessage("", ""), CommitMessage.parse(" \n"));
    }

    @Test
    void parsingWhatAMessageIsGivesItBack() {
        CommitMessage message = new CommitMessage("Fix it", "Because.");

        assertEquals(message, CommitMessage.parse(message.text()));
    }

    @Test
    void aRevertNamesTheSummaryAndTheCommitItReverts() {
        assertEquals(
                new CommitMessage("Revert \"Fix it\"", "This reverts commit aaaa."),
                CommitMessage.revertOf(commit("Fix it", List.of(new Sha("pppp")))));
    }

    @Test
    void theRevertOfAMergeNamesTheChangesItReverses() {
        assertEquals(
                new CommitMessage(
                        "Revert \"Merge side\"", "This reverts commit aaaa, reversing\nchanges made to pppp."),
                CommitMessage.revertOf(commit("Merge side", List.of(new Sha("pppp"), new Sha("ssss")))));
    }

    @Test
    void theRevertOfARevertReappliesIt() {
        assertEquals(
                "Reapply \"Fix it\"",
                CommitMessage.revertOf(commit("Revert \"Fix it\"", List.of())).summary());
    }

    @Test
    void theRevertOfARevertOfARevertIsLeftAlone() {
        assertEquals(
                "Revert \"Revert \"Revert \"Fix it\"\"\"",
                CommitMessage.revertOf(commit("Revert \"Revert \"Fix it\"\"", List.of()))
                        .summary());
    }

    private static Commit commit(String subject, List<Sha> parents) {
        return new Commit(new Sha("aaaa"), "Ada", "ada@example.com", Instant.EPOCH, subject, "", parents);
    }
}
