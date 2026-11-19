package net.vanfleteren.jazygit.kernel.state;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.vanfleteren.jazygit.core.git.model.CommitMessage;
import net.vanfleteren.jazygit.features.changes.commit.create.CommitPopup;
import org.junit.jupiter.api.Test;

/**
 * Whether the app stops on {@link Msg.Quit}: not while a popup holds typed input.
 */
class QuitUpdateTest {

    @Test
    void quitsWithoutAPopup() {
        assertTrue(Update.update(loaded(), new Msg.Quit()).quit());
    }

    @Test
    void isRefusedWhileTheCommitDialogHoldsATypedMessage() {
        Model model = loaded().openPopup(new CommitPopup().withMessage(new CommitMessage("hi", "")));

        assertFalse(Update.update(model, new Msg.Quit()).quit());
    }

    @Test
    void isRefusedWhileTheCommitDialogHoldsATypedDescription() {
        Model model = loaded().openPopup(new CommitPopup().withMessage(new CommitMessage("", "details")));

        assertFalse(Update.update(model, new Msg.Quit()).quit());
    }

    @Test
    void quitsWhileTheCommitMessageIsBlank() {
        Model empty = loaded().openPopup(new CommitPopup());
        Model blank = loaded().openPopup(new CommitPopup().withMessage(new CommitMessage("  ", " ")));

        assertTrue(Update.update(empty, new Msg.Quit()).quit());
        assertTrue(Update.update(blank, new Msg.Quit()).quit());
    }
}
