package net.vanfleteren.jazygit.feature.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitMessage;

/**
 * Asking for the message of a new commit.
 *
 * @param draft the message to show, once it came back from the editor
 */
public record CommitPopup(Option<CommitMessage> draft) implements MessagePopup {

    /**
     * Asking for it from scratch.
     */
    public CommitPopup() {
        this(Option.none());
    }

    @Override
    public CommitPopup withDraft(CommitMessage draft) {
        return new CommitPopup(Option.of(draft));
    }
}
