package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.commit.RewordMsg.Requested;

import net.vanfleteren.jazygit.feature.commit.RewordMsg.Failed;

import net.vanfleteren.jazygit.feature.commit.RewordMsg.Done;

import net.vanfleteren.jazygit.feature.commit.RewordMsg.Confirmed;

import net.vanfleteren.jazygit.feature.commit.RewordMsg.Cancelled;

import net.vanfleteren.jazygit.feature.commit.CommitCmd.Reword;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Update.Next;
import java.util.List;
import java.util.Optional;

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
            case Confirmed(String summary, String description) -> confirmed(model, summary, description);
            case Done(List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Optional.empty()), "log.title.reword", commands));
            case Failed(String message) -> Update.refresh(model.withError(Optional.of(Messages.get("error.reword.failed", message))));
        };
    }

    /**
     * Any commit can be reworded; the dialog starts out with its message.
     */
    private static Next requested(Model model, int index) {
        return model.commits() instanceof Loaded<List<Commit>>(var commits) && index >= 0 && index < commits.size()
                ? Next.of(model.withError(Optional.empty()).openPopup(new RewordPopup(commits.get(index))))
                : Next.of(model);
    }

    private static Next confirmed(Model model, String summary, String description) {
        String trimmed = summary.strip();
        // A blank summary keeps the dialog open.
        return model.popup(RewordPopup.class).filter(popup -> !trimmed.isEmpty())
                .map(popup -> Next.of(model.withoutPopup(RewordPopup.class).withError(Optional.empty()),
                        new Reword(popup.commit().sha(), trimmed, description.strip())))
                .orElseGet(() -> Next.of(model));
    }
}
