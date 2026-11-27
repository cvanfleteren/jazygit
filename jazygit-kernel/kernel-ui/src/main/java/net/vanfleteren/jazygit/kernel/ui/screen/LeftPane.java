package net.vanfleteren.jazygit.kernel.ui.screen;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.view.Followed;

/**
 * A pane of the left column that lives in a group of features (ADR 158), which brings it to its screen with
 * {@code Feature.pane} (ADR 211).
 */
public interface LeftPane {

    /**
     * The id of the pane, by which its screen lists it ({@code Screen.panes}) and the focus names it.
     */
    PaneId pane();

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
     * What the files pane lists while this pane has the focus (ADR 141), if it lists something of this pane: none
     * while that is not known yet, or for a pane the files pane does not follow.
     */
    default Option<Followed> followed(Model model) {
        return Option.none();
    }
}
