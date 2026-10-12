package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;

import java.util.function.Consumer;

/**
 * Popup asking how to rebase the checked out branch onto the highlighted one.
 */
public final class RebaseDialog {

    public static final String ID = "rebase";

    private RebaseDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ChoiceDialog<RebasePopup> create(Consumer<Msg> dispatch) {
        return new ChoiceDialog<>(ID, RebasePopup.class,
                popup -> Messages.get("dialog.rebase.title", popup.branch()),
                List.of(new Choice<RebasePopup>('s', popup -> Messages.get("dialog.rebase.simple", popup.onto()),
                        RebaseMsg.Chosen::new)),
                RebaseMsg.Cancelled::new, dispatch);
    }
}
