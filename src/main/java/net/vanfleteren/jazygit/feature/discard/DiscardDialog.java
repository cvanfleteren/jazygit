package net.vanfleteren.jazygit.feature.discard;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;

import java.util.function.Consumer;

/**
 * Popup asking what to discard. The option for unstaged changes only applies when there are some.
 */
public final class DiscardDialog {

    public static final String ID = "discard";

    private DiscardDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ChoiceDialog<DiscardPopup> create(Consumer<Msg> dispatch) {
        return new ChoiceDialog<>(ID, DiscardPopup.class,
                popup -> Messages.get("dialog.discard.title"),
                List.of(new Choice<>('x', "dialog.discard.all", () -> new DiscardMsg.Chosen(Scope.ALL)),
                        new Choice<>('u', "dialog.discard.unstaged", () -> new DiscardMsg.Chosen(Scope.UNSTAGED),
                                (model, popup) -> popup.hasUnstaged())),
                DiscardMsg.Cancelled::new, dispatch);
    }
}
