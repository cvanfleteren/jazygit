package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to push {@code branch}, which has no upstream yet.
 */
public record SetUpstreamPopup(String branch) implements Popup {
}
