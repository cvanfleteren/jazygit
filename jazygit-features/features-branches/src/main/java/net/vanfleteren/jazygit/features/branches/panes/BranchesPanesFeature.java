package net.vanfleteren.jazygit.features.branches.panes;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.KernelSlots;

/**
 * The branches pane of the repository screen, which this group owns (ADR 158) and brings to the screen itself (ADR
 * 211), with the times it shows and its spinners. Its keys are those of the features that act on it.
 */
public final class BranchesPanesFeature {

    /**
     * The pane; the times it shows, with those of the badges the features put on its branches, which wake the app as
     * they change (ADR 124); and whether it shows a spinner, which keeps the frames coming.
     */
    public static final Feature FEATURE = Feature.of()
            .pane(
                    Screen.REPOSITORY,
                    context -> new BranchesPanel(
                            context.dispatch(), context.clock(), context.highlights(), context.fills()))
            .reading(
                    KernelSlots.CLOCKING, (model, fills) -> BranchesPanel.shownTimes(model, BranchBadges.filled(fills)))
            .filling(KernelSlots.SPINNING, model -> Option.when(BranchesPanel.showsSpinner(model), true));

    private BranchesPanesFeature() {}
}
