package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Reword;
import net.vanfleteren.jazygit.feature.commit.RewordMsg.Cancelled;
import net.vanfleteren.jazygit.feature.commit.RewordMsg.Confirmed;
import net.vanfleteren.jazygit.feature.commit.RewordMsg.Requested;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Rewording a commit.
 */
public final class RewordUpdate {

    private RewordUpdate() {
    }

    public static Next update(Model model, RewordMsg msg) {
        return switch (msg) {
            case Requested(int index) -> requested(model, index);
            case Cancelled() -> Next.of(model.withoutPopup(RewordPopup.class));
            case Confirmed(CommitMessage message) -> confirmed(model, message.stripped());
        };
    }

    /**
     * Any commit can be reworded; the dialog starts out with its message.
     */
    private static Next requested(Model model, int index) {
        return model.commits() instanceof Loaded<List<Commit>>(var commits) && index >= 0 && index < commits.size()
                ? Next.of(model.clearError().openPopup(new RewordPopup(commits.get(index))))
                : Next.of(model);
    }

    private static Next confirmed(Model model, CommitMessage message) {
        // A blank summary keeps the dialog open.
        return model.popup(RewordPopup.class).filter(popup -> message.hasSummary())
                .map(popup -> Next.of(model.withoutPopup(RewordPopup.class).clearError(),
                        new Reword(popup.commit().sha(), message)))
                .getOrElse(() -> Next.of(model));
    }
}
