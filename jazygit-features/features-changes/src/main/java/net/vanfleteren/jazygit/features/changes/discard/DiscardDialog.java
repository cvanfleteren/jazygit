package net.vanfleteren.jazygit.features.changes.discard;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.features.changes.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

/**
 * Popup asking what to discard. The option for unstaged changes only applies when there are some.
 */
public final class DiscardDialog {

    public static final ChoiceDialog.Spec<DiscardPopup> SPEC = new ChoiceDialog.Spec<>(
            "discard",
            DiscardPopup.class,
            popup -> Messages.get("changes.dialog.discard.title"),
            List.of(
                    new Choice<DiscardPopup>('x', "changes.dialog.discard.all", () -> new DiscardMsg.Chosen(Scope.ALL))
                            .withDetails("changes.dialog.discard.all.details"),
                    new Choice<DiscardPopup>(
                                    'u',
                                    "changes.dialog.discard.unstaged",
                                    () -> new DiscardMsg.Chosen(Scope.UNSTAGED),
                                    (model, popup) -> popup.hasUnstaged())
                            .withDetails("changes.dialog.discard.unstaged.details")));

    private DiscardDialog() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(PaneId.FILES, new ChoiceDialog<>(SPEC, dispatch)));
    }
}
