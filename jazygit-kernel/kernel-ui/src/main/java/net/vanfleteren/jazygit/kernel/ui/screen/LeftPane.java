package net.vanfleteren.jazygit.kernel.ui.screen;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * A pane of the left column that lives in a group of features (ADR 158), which the app gives the
 * {@link net.vanfleteren.jazygit.kernel.ui.LeftColumn}.
 */
public interface LeftPane {

    /**
     * The pane as {@code model} shows it.
     *
     * @param focus what has the focus, if anything
     * @param inUse the left pane in use, whose highlighted row shows
     */
    Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse);

    /**
     * The message to send when the pane highlights another item than the one {@code model} holds the preview of.
     */
    Option<Msg> selectionChange(Model model);

    /**
     * What makes a pane, once the column knows how the theme highlights a row.
     */
    @FunctionalInterface
    interface Factory {
        LeftPane of(Theme.Highlights highlights);
    }
}
