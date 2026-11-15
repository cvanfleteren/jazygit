package net.vanfleteren.jazygit.changes.commit.reword;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.changes.commit.CommitDialog;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;

/**
 * The popup of rewording a commit: the commit dialog, starting out with its message.
 */
public final class RewordDialogs {

    private RewordDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.PANE, CommitDialog.reword(dispatch)));
    }
}
