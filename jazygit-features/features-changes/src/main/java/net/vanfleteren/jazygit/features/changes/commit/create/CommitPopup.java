package net.vanfleteren.jazygit.features.changes.commit.create;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.git.model.CommitMessage;
import net.vanfleteren.jazygit.features.changes.commit.MessagePopup;

/**
 * Asking for the message of a new commit.
 *
 * @param message the message as far as it was typed
 */
@With
public record CommitPopup(CommitMessage message) implements MessagePopup {

    /**
     * Asking for it from scratch.
     */
    public CommitPopup() {
        this(new CommitMessage("", ""));
    }

    @Override
    public Option<CreateCommitCmd.Commit> confirm() {
        // A blank summary keeps the dialog open.
        CommitMessage stripped = message.stripped();
        return Option.when(stripped.hasSummary(), () -> new CreateCommitCmd.Commit(stripped));
    }
}
