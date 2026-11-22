package net.vanfleteren.jazygit.features.branches.branch.upstream;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

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
                    .map(branch -> Messages.get(
                            "branches.dialog.upstream.title", branch.name().value()))
                    .getOrElse(() -> Messages.get("branches.dialog.upstream.titleDetached")),
            List.of(
                    new Choice<UpstreamPopup>(
                                    'v',
                                    popup -> Messages.get(
                                            "branches.dialog.upstream.divergence", upstreamName(popup.branch())),
                                    UpstreamMsg.UpstreamDivergenceChosen::new)
                            .withDetails("branches.dialog.upstream.divergence.details"),
                    new Choice<UpstreamPopup>(
                                    'b',
                                    popup -> popup.base()
                                            .map(base -> Messages.get(
                                                    "branches.dialog.upstream.baseDivergence", base.name()))
                                            .getOrElse(() ->
                                                    Messages.get("branches.dialog.upstream.baseDivergenceUnknown")),
                                    UpstreamMsg.BaseDivergenceChosen::new)
                            .withDetails("branches.dialog.upstream.baseDivergence.details"),
                    new Choice<UpstreamPopup>(
                                    'g',
                                    popup -> Messages.get(
                                            "branches.dialog.upstream.reset", upstreamName(popup.checkedOut())),
                                    UpstreamMsg.ResetChosen::new)
                            .withDetails("branches.dialog.upstream.reset.details")));

    private UpstreamDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ChoiceDialog<>(UPSTREAM, dispatch)));
    }

    private static String upstreamName(Option<Branch> branch) {
        return branch.flatMap(Branch::upstream)
                .map(upstream -> upstream.branch().name())
                .getOrElse(() -> Messages.get("branches.dialog.upstream.unknown"));
    }
}
