package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

import java.util.function.Consumer;

/**
 * Popup asking how to merge the highlighted branch into the checked out one.
 */
public final class MergeDialog {

    public static final String ID = "merge";

    private MergeDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ChoiceDialog<MergePopup> create(Consumer<Msg> dispatch) {
        return new ChoiceDialog<>(ID, MergePopup.class,
                popup -> Messages.get("dialog.merge.title", popup.branch(), popup.into()),
                List.of(new Choice<>('m', "dialog.merge.fastForward", () -> new MergeMsg.Chosen(MergeMode.FAST_FORWARD)),
                        new Choice<>('n', "dialog.merge.mergeCommit", () -> new MergeMsg.Chosen(MergeMode.MERGE_COMMIT)),
                        new Choice<>('s', "dialog.merge.squash", () -> new MergeMsg.Chosen(MergeMode.SQUASH)),
                        new Choice<>('S', "dialog.merge.squashAndCommit",
                                () -> new MergeMsg.Chosen(MergeMode.SQUASH_AND_COMMIT))),
                MergeMsg.Cancelled::new, dispatch);
    }
}
