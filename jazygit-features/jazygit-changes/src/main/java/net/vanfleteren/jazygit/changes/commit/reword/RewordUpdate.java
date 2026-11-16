package net.vanfleteren.jazygit.changes.commit.reword;

import net.vanfleteren.jazygit.changes.commit.reword.RewordMsg.Requested;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.popup.PopupUpdate;

/**
 * Rewording a commit.
 */
public final class RewordUpdate {

    private RewordUpdate() {}

    public static Next update(Model model, RewordMsg msg) {
        return switch (msg) {
            case Requested(var sha) -> requested(model, sha);
        };
    }

    /**
     * Any commit can be reworded; the dialog starts out with its message.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha).map(RewordPopup::new).transform(popup -> PopupUpdate.ask(model, popup));
    }
}
