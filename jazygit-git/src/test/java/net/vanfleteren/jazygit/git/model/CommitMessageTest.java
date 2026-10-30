package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
