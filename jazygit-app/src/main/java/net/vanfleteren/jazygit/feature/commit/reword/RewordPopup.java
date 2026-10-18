package net.vanfleteren.jazygit.feature.commit.reword;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.MessagePopup;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;

/**
 * Asking for a new message of a commit, starting out with its current one.
 *
 * @param draft the message to show instead, once it came back from the editor
 */
public record RewordPopup(Commit commit, Option<CommitMessage> draft) implements MessagePopup {

    public RewordPopup(Commit commit) {
        this(commit, Option.none());
    }

    @Override
    public RewordPopup withDraft(CommitMessage draft) {
        return new RewordPopup(commit, Option.of(draft));
    }

    /**
     * The message the dialog shows: the draft, else the current message of the commit.
     */
    public CommitMessage message() {
        return draft.getOrElse(() -> new CommitMessage(commit.message(), commit.body()));
    }
}
