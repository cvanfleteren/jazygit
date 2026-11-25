package net.vanfleteren.jazygit.features.midway.plan;

import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPane;
import net.vanfleteren.jazygit.features.midway.plan.content.PlanRows;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * The steps of the operation the repository stopped in the middle of, listed above the log of the commits pane
 * (ADR 199).
 */
public final class PlanFeature {

    /**
     * What the app knows of it: what it lists above the log.
     */
    public static final Feature FEATURE = Feature.of().filling(CommitsPane.ABOVE, PlanRows::of);

    private PlanFeature() {}
}
