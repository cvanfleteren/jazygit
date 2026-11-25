package net.vanfleteren.jazygit.features.midway.inprogress;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Plan;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking whether to continue, abort or skip {@code inProgress}, with {@code conflicts} files still conflicted.
 *
 * @param marked the files resolved since it stopped that still have conflict markers in them, which continuing
 *               commits (ADR 198)
 * @param plan   its steps, as git keeps them, which say where it stopped and what continuing goes on with (ADR 199)
 */
public record InProgressPopup(InProgress inProgress, int conflicts, List<String> marked, Option<Plan> plan)
        implements Popup {

    /**
     * Asking about {@code inProgress}, whose steps are not known.
     */
    public InProgressPopup(InProgress inProgress, int conflicts, List<String> marked) {
        this(inProgress, conflicts, marked, Option.none());
    }

    /**
     * Asking about {@code inProgress}, with no resolved file that has conflict markers left.
     */
    public InProgressPopup(InProgress inProgress, int conflicts) {
        this(inProgress, conflicts, List.empty());
    }
}
