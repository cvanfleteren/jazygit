package net.vanfleteren.jazygit.kernel.ui.screen;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * The files pane, which lives in a group of features (ADR 158) as the other panes of the left column do, and which
 * the column asks for more than for them: to step to another file without the focus (ADR 117, 207), and to list what
 * the pane in use highlights (ADR 141).
 */
public interface FilesLeftPane extends LeftPane {

    /**
     * Moves the highlight to the file {@code step} of the one highlighted, keeping the focus where it is, and says
     * whether there was one (ADR 117).
     */
    boolean step(Model model, FileStepMsg.Step step);

    /**
     * The message to send when the pane lists other files than those the pane in use, {@code inUse}, follows (ADR
     * 141); {@code branches} is what the branches follow.
     */
    Option<Msg> followChange(Model model, Option<PaneId> inUse, Option<Followed> branches);

    /**
     * What makes the files pane, once the column knows how to open the content of the pane and how the theme
     * highlights a row.
     */
    @FunctionalInterface
    interface Factory {

        /**
         * @param openDiff moves the focus to the diff of what the pane highlights, as Enter or Right does
         */
        FilesLeftPane of(Runnable openDiff, Theme.Highlights highlights);
    }
}
