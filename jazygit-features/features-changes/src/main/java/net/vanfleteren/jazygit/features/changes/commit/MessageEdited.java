package net.vanfleteren.jazygit.features.changes.commit;

import net.vanfleteren.jazygit.core.repo.CommitMessage;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * The user changed the message of the open {@link MessagePopup} to {@code message}.
 */
public record MessageEdited(CommitMessage message) implements Msg {

    @Override
    public Update.Next apply(Model model) {
        return Update.Next.of(model.popup(MessagePopup.class)
                .map(popup -> model.openPopup(popup.withMessage(message)))
                .getOrElse(model));
    }
}
