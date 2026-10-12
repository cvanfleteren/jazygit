package net.vanfleteren.jazygit.feature.discard;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;

/**
 * Popup asking what to discard. The option for unstaged changes only applies when there are some.
 */
public final class DiscardDialog {

    public static final ChoiceDialog.Spec<DiscardPopup> SPEC = new ChoiceDialog.Spec<>("discard", DiscardPopup.class,
            popup -> Messages.get("dialog.discard.title"),
            List.of(new Choice<>('x', "dialog.discard.all", () -> new DiscardMsg.Chosen(Scope.ALL)),
                    new Choice<>('u', "dialog.discard.unstaged", () -> new DiscardMsg.Chosen(Scope.UNSTAGED),
                            (model, popup) -> popup.hasUnstaged())),
            DiscardMsg.Cancelled::new);

    private DiscardDialog() {
    }
}
