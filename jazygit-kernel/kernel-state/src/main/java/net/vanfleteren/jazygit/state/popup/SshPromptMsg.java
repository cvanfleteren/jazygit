package net.vanfleteren.jazygit.state.popup;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * ssh asks the user for a secret, and stops asking (ADR 150). No message carries what is typed, nor how much.
 */
public sealed interface SshPromptMsg extends Msg {

    /**
     * ssh asks {@code prompt}, as the question {@code request}: its popup opens over the one that is open, which comes
     * back once it closes.
     */
    record Asked(long request, String prompt) implements SshPromptMsg {

        @Override
        public Update.Next apply(Model model) {
            SshPromptPopup asking = model.popup(SshPromptPopup.class)
                    .map(open -> new SshPromptPopup(request, prompt, open.under(), open.underCursor()))
                    .getOrElse(() -> new SshPromptPopup(request, prompt, model.popup(), model.listCursor()));
            return Update.Next.of(model.withPopup(Option.of(asking)).withListCursor(ListCursor.START));
        }
    }

    /**
     * The question {@code request} was answered, cancelled or withdrawn: its popup closes, and the one under it comes
     * back as it was.
     */
    record Closed(long request) implements SshPromptMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.popup(SshPromptPopup.class)
                    .filter(open -> open.request() == request)
                    .map(open -> model.withPopup(open.under()).withListCursor(open.underCursor()))
                    .getOrElse(model));
        }
    }
}
