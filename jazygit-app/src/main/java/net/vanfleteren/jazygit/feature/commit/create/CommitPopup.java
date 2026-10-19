package net.vanfleteren.jazygit.feature.commit.create;

import net.vanfleteren.jazygit.feature.commit.MessagePopup;
import net.vanfleteren.jazygit.git.model.CommitMessage;

/**
 * Asking for the message of a new commit.
 *
 * @param message the message as far as it was typed
 */
public record CommitPopup(CommitMessage message) implements MessagePopup {

    /**
     * Asking for it from scratch.
     */
    public CommitPopup() {
        this(new CommitMessage("", ""));
    }

    @Override
    public CommitPopup withMessage(CommitMessage message) {
        return new CommitPopup(message);
    }
}
