package net.vanfleteren.jazygit.feature.commit.revert;

import net.vanfleteren.jazygit.feature.commit.MessagePopup;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;

/**
 * Asking for the message of the revert of a commit, which is also the confirmation of reverting it.
 *
 * @param message the message as far as it was typed; it starts out as the one git would write
 */
public record RevertPopup(Commit commit, CommitMessage message) implements MessagePopup {

    public RevertPopup(Commit commit) {
        this(commit, CommitMessage.revertOf(commit));
    }

    @Override
    public RevertPopup withMessage(CommitMessage message) {
        return new RevertPopup(commit, message);
    }
}
