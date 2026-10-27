package net.vanfleteren.jazygit.feature.start;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.state.Msg;
import org.junit.jupiter.api.Test;

/**
 * Outside a repository, Escape does more than close the popup (ADR 38): without these, the user is left with none.
 */
class StartDialogsTest {

    @Test
    void escapeOnTheChoiceQuits() {
        assertEquals(new Msg.Quit(), StartDialogs.NO_REPOSITORY.onCancel().get());
    }

    @Test
    void escapeOnOpeningAFolderGoesBackToTheChoice() {
        assertEquals(
                new StartMsg.FolderCancelled(),
                StartDialogs.OPEN_FOLDER.onCancel().get());
    }

    @Test
    void escapeOnOpeningARecentRepositoryGoesBackToTheChoice() {
        assertEquals(
                new StartMsg.RecentCancelled(),
                StartDialogs.OPEN_RECENT.onCancel().get());
    }
}
