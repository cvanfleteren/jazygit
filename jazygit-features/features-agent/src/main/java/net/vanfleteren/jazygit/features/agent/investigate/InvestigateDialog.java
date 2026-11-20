package net.vanfleteren.jazygit.features.agent.investigate;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Cell;

/**
 * The menu of the agents a failed build can be handed to (ADR 161): each with the letter that chooses it and its name.
 */
public final class InvestigateDialog {

    public static final PickDialog.Spec<InvestigatePopup, AgentProgram> MENU = new PickDialog.Spec<>(
            "investigate",
            InvestigatePopup.class,
            popup -> Messages.get(
                    "agent.dialog.investigate.title", popup.build().branch().value()),
            popup -> popup.agents()
                    .map(lettered -> new PickDialog.Row<>(lettered._2, List.of(Cell.of(lettered._2.name())))
                            .withKey(lettered._1)),
            InvestigateMsg.Chosen::new,
            Option.none());

    private InvestigateDialog() {}
}
