package net.vanfleteren.jazygit.feature.branch.upstream;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * The popup of the upstream options of a branch.
 */
public final class UpstreamDialogs {

    /**
     * Asks what to do with the upstream: view how the highlighted branch diverged from it or from the main
     * branch, or reset the checked out branch onto it. Its options are offered also when they cannot be done, to
     * tell why.
     */
    public static final ChoiceDialog.Spec<UpstreamPopup> UPSTREAM = new ChoiceDialog.Spec<>(
            "upstream",
            UpstreamPopup.class,
            popup -> popup.branch()
                    .map(branch ->
                            Messages.get("dialog.upstream.title", branch.name().value()))
                    .getOrElse(() -> Messages.get("dialog.upstream.titleDetached")),
            List.of(
                    new Choice<UpstreamPopup>(
                            'v',
                            popup -> Messages.get("dialog.upstream.divergence", upstreamName(popup.branch())),
                            UpstreamMsg.UpstreamDivergenceChosen::new),
                    new Choice<UpstreamPopup>(
                            'b',
                            popup -> popup.base()
                                    .map(base -> Messages.get("dialog.upstream.baseDivergence", base.name()))
                                    .getOrElse(() -> Messages.get("dialog.upstream.baseDivergenceUnknown")),
                            UpstreamMsg.BaseDivergenceChosen::new),
                    new Choice<UpstreamPopup>(
                            'g',
                            popup -> Messages.get("dialog.upstream.reset", upstreamName(popup.checkedOut())),
                            UpstreamMsg.ResetChosen::new)),
            UpstreamMsg.Cancelled::new);

    private UpstreamDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.ID, new ChoiceDialog<>(UPSTREAM, dispatch)));
    }

    private static String upstreamName(Option<Branch> branch) {
        return branch.flatMap(Branch::upstream)
                .map(upstream -> upstream.branch().shortName())
                .getOrElse(() -> Messages.get("dialog.upstream.unknown"));
    }
}
