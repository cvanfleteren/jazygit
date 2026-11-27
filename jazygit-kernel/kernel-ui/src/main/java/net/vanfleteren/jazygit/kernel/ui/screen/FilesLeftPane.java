package net.vanfleteren.jazygit.kernel.ui.screen;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.Followed;

/**
 * The files pane, which lives in a group of features (ADR 158) and is brought to the repository screen as the other
 * panes are (ADR 211), and which the column asks for more than for them: to step to another file without the focus
 * (ADR 117, 207), and to list what the pane in use highlights (ADR 141).
 */
public interface FilesLeftPane extends LeftPane {

    /**
     * Moves the highlight to the file {@code step} of the one highlighted, keeping the focus where it is, and says
     * whether there was one (ADR 117).
     */
    boolean step(Model model, FileStepMsg.Step step);

    /**
     * The message to send when the pane lists other files than those the pane in use, {@code inUse}, follows (ADR
     * 141); {@code followed} is what each pane of the screen says it follows ({@link LeftPane#followed}).
     */
    Option<Msg> followChange(Model model, Option<PaneId> inUse, Function<PaneId, Option<Followed>> followed);
}
