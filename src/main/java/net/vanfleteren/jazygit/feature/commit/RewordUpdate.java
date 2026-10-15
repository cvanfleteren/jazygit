package net.vanfleteren.jazygit.feature.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Reword;
import net.vanfleteren.jazygit.feature.commit.RewordMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.RewordMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.RewordMsg.Requested;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Rewording a commit.
 */
public final class RewordUpdate {

    private RewordUpdate() {
    }

    public static Next update(Model model, RewordMsg msg) {
        return switch (msg) {
            case Requested(var sha) -> requested(model, sha);
            case Cancelled() -> Next.of(model.withoutPopup(RewordPopup.class));
            case Confirmed(CommitMessage message) -> confirmed(model, message.stripped());
        };
    }

    /**
     * Any commit can be reworded; the dialog starts out with its message.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha)
                .map(RewordPopup::new)
                .transform(popup -> Update.ask(model, popup));
    }

    private static Next confirmed(Model model, CommitMessage message) {
        // A blank summary keeps the dialog open.
        return Update.confirm(model, RewordPopup.class,
                popup -> Option.when(message.hasSummary(), () -> new Reword(popup.commit().sha(), message)));
    }
}
