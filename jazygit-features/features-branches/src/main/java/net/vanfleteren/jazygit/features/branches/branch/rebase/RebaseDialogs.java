package net.vanfleteren.jazygit.features.branches.branch.rebase;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

/**
 * The popup of rebasing the checked out branch.
 */
public final class RebaseDialogs {

    /**
     * Asks where to rebase the checked out branch: onto the highlighted one, or on its base branch (ADR 91). An
     * option that does not apply is shown disabled; on the checked out row the dialog opens on the base branch.
     */
    public static final ChoiceDialog.Spec<RebasePopup> REBASE = new ChoiceDialog.Spec<>(
                    "rebase",
                    RebasePopup.class,
                    popup -> Messages.get(
                            "branches.dialog.rebase.title", popup.branch().value()),
                    List.of(
                            new Choice<RebasePopup>(
                                            's',
                                            popup -> Messages.get(
                                                    "branches.dialog.rebase.simple",
                                                    popup.highlighted().value()),
                                            () -> new RebaseMsg.Chosen(RebaseMsg.Target.ONTO),
                                            (model, popup) -> RebaseUpdate.onto(model, popup, RebaseMsg.Target.ONTO)
                                                    .isDefined())
                                    .withDetails("branches.dialog.rebase.simple.details"),
                            new Choice<RebasePopup>(
                                            'b',
                                            popup -> popup.base()
                                                    .map(base ->
                                                            Messages.get("branches.dialog.rebase.base", base.value()))
                                                    .getOrElse(() -> Messages.get("branches.dialog.rebase.noBase")),
                                            () -> new RebaseMsg.Chosen(RebaseMsg.Target.BASE),
                                            (model, popup) -> RebaseUpdate.onto(model, popup, RebaseMsg.Target.BASE)
                                                    .isDefined())
                                    .withDetails("branches.dialog.rebase.base.details")))
            .openingAt(popup -> popup.onto().isEmpty() ? Option.of('b') : Option.none());

    private RebaseDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.PANE, new ChoiceDialog<>(REBASE, dispatch)));
    }
}
