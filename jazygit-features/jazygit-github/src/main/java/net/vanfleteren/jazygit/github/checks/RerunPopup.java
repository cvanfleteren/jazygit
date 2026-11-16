package net.vanfleteren.jazygit.github.checks;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking to run the failed jobs of {@code plan} again, over the checks popup {@code checks}, which both Enter and
 * Escape go back to (ADR 135).
 */
public record RerunPopup(ChecksPopup checks, RerunPlan plan) implements Popup {

    /**
     * This, over {@code checks} as they are read meanwhile.
     */
    public RerunPopup over(ChecksPopup checks) {
        return new RerunPopup(checks, plan);
    }

    /**
     * The checks it goes back to keep how far they were shown to be, as when they are shown.
     */
    @Override
    public Popup checkTimesChanging(Model before) {
        return over(checks.checkTimesChanging(before));
    }
}
