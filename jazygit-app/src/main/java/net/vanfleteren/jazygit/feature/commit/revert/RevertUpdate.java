package net.vanfleteren.jazygit.feature.commit.revert;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.revert.RevertMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.revert.RevertMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.revert.RevertMsg.Requested;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reverting a commit. Nothing is checked up front: git refuses when uncommitted changes would be overwritten, and
 * fails a revert that changes nothing.
 */
public final class RevertUpdate {

    private RevertUpdate() {}

    public static Next update(Model model, RevertMsg msg) {
        return switch (msg) {
            case Requested(var sha) -> requested(model, sha);
            case Cancelled() -> Next.of(model.withoutPopup(RevertPopup.class));
            case Confirmed() -> confirmed(model);
        };
    }

    /**
     * Any commit can be reverted, a merge against its first parent; the dialog starts out with git's message.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha).map(RevertPopup::new).transform(popup -> Update.ask(model, popup));
    }

    private static Next confirmed(Model model) {
        // A blank summary keeps the dialog open.
        return Update.confirm(model, RevertPopup.class, popup -> {
            CommitMessage message = popup.message().stripped();
            return Option.when(message.hasSummary(), () -> new RevertCmd(popup.commit(), message));
        });
    }
}
