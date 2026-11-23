package net.vanfleteren.jazygit.kernel.state.view;

import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.Scrolled;

/**
 * The user changed what the screen, outside the popups, shows: which pane or area has the focus, how far an area is
 * scrolled, or which tab a pane shows.
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

    /**
     * The user asked {@code pane} to show its tab {@code tab} (ADR 185).
     */
    record TabChosen(PaneId pane, TabId tab) implements ViewMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.mapRepoView(view -> view.withTab(pane, tab)));
        }
    }
}
