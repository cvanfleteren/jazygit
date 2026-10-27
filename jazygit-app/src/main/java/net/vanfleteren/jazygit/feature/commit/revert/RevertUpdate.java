package net.vanfleteren.jazygit.feature.commit.revert;

import net.vanfleteren.jazygit.feature.commit.revert.RevertMsg.Requested;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reverting a commit, with the message git writes. Nothing is checked up front: git refuses when uncommitted changes
 * would be overwritten, and fails a revert that changes nothing.
 */
public final class RevertUpdate {

    private RevertUpdate() {}

    public static Next update(Model model, RevertMsg msg) {
        return switch (msg) {
            case Requested(var sha) -> requested(model, sha);
        };
    }

    /**
     * Any commit can be reverted, a merge against its first parent.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha).map(RevertPopup::new).transform(popup -> Update.ask(model, popup));
    }
}
