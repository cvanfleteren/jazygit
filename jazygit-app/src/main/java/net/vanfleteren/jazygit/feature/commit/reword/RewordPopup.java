package net.vanfleteren.jazygit.feature.commit.reword;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.feature.commit.MessagePopup;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;

/**
 * Asking for a new message of a commit.
 *
 * @param message the new message as far as it was typed; it starts out as the current message of the commit
 */
@With
public record RewordPopup(Commit commit, CommitMessage message) implements MessagePopup {

    public RewordPopup(Commit commit) {
        this(commit, new CommitMessage(commit.message(), commit.body()));
    }

    @Override
    public Option<RewordCmd> confirm() {
        // A blank summary keeps the dialog open.
        CommitMessage stripped = message.stripped();
        return Option.when(stripped.hasSummary(), () -> new RewordCmd(commit.sha(), stripped));
    }
}
