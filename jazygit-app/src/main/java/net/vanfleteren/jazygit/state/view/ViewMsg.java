package net.vanfleteren.jazygit.state.view;

import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
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
     * The pane or area {@code focus} of the screen shown, outside the popups, got the focus.
     */
    record Focused(Focus.OnScreen focus) implements ViewMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withShownPane(focus));
        }
    }

    /**
     * The user scrolled the content area {@code area}.
     */
    record ContentScrolled(AreaId area, Scrolled to) implements ViewMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withScrolled(model.scrolled().put(area, to)));
        }
    }
}
