package net.vanfleteren.jazygit.state.view;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.Scrolled;

/**
 * The user changed what the screen, outside the popups, shows: which pane or area has the focus, or how far an area
 * is scrolled.
 */
public sealed interface ViewMsg extends Msg {

    /**
     * The element with the id {@code id} of the screen shown, outside the popups, got the focus.
     *
     * @param content whether it is an area of the content panel, rather than a pane
     */
    record Focused(String id, boolean content) implements ViewMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withShownPane(id, content));
        }
    }

    /**
     * The user scrolled the content area {@code area}.
     */
    record ContentScrolled(String area, Scrolled to) implements ViewMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withScrolled(model.scrolled().put(area, to)));
        }
    }
}
