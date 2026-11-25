package net.vanfleteren.jazygit.features.forgejo.checks;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking to run the failed jobs of {@code plan} again, over the checks popup {@code checks}, which both Enter and
 * Escape go back to (ADR 135, 195).
 */
public record RerunPopup(ForgejoChecksPopup checks, RerunPlan plan) implements Popup {

    /**
     * This, over {@code checks} as they are read meanwhile.
     */
    public RerunPopup over(ForgejoChecksPopup checks) {
        return new RerunPopup(checks, plan);
    }
}
