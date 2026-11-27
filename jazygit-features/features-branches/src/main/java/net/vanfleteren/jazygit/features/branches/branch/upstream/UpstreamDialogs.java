package net.vanfleteren.jazygit.features.branches.branch.upstream;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.TextInputDialog;

/**
 * The popup of the upstream options of a branch.
 */
public final class UpstreamDialogs {

    /**
     * Asks what to do with the upstream: view how the highlighted branch diverged from it or from the main
     * branch, reset the checked out branch onto it, or set or unset the upstream of the highlighted branch. Its
     * options are offered also when they cannot be done, to tell why.
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
                            .withDetails("branches.dialog.upstream.reset.details"),
                    new Choice<UpstreamPopup>('s', "branches.dialog.upstream.set", UpstreamMsg.SetChosen::new)
                            .withDetails("branches.dialog.upstream.set.details"),
                    new Choice<UpstreamPopup>(
                                    'u',
                                    "branches.dialog.upstream.unset",
                                    UpstreamMsg.UnsetChosen::new,
                                    (model, popup) -> followsSomething(popup))
                            .withDetails(popup -> Messages.get(
                                    followsSomething(popup)
                                            ? "branches.dialog.upstream.unset.details"
                                            : "branches.dialog.upstream.unset.none"))));

    /**
     * Asks which remote branch a branch is to follow, as {@code <remote> <branchname>}, starting out with the branch
     * of its own name. What cannot be read that way, or is not on the remote as last fetched, keeps it open.
     */
    public static final TextInputDialog.Spec<ChangeUpstreamPopup> CHANGE_UPSTREAM = new TextInputDialog.Spec<>(
                    "change-upstream",
                    ChangeUpstreamPopup.class,
                    popup -> Messages.get(
                            "branches.dialog.changeUpstream.title",
                            popup.branch().value()),
                    Option.none(),
                    UpstreamMsg.SetConfirmed::new)
            .withWidth(TextInputDialog.BRANCH_NAME_WIDTH);

    private UpstreamDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ChoiceDialog<>(UPSTREAM, dispatch)),
                PopupSlot.returningTo(Scopes.BRANCHES.pane(), new TextInputDialog<>(CHANGE_UPSTREAM, dispatch)));
    }

    /**
     * Whether the highlighted branch has an upstream configured, also one that was deleted on the remote.
     */
    private static boolean followsSomething(UpstreamPopup popup) {
        return popup.branch().exists(branch -> !(branch.tracking() instanceof Tracking.None));
    }

    private static String upstreamName(Option<Branch> branch) {
        return branch.flatMap(Branch::upstream)
                .map(upstream -> upstream.branch().name())
                .getOrElse(() -> Messages.get("branches.dialog.upstream.unknown"));
    }
}
