package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
