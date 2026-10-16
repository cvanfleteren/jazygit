package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to push {@code branch}, which has no upstream yet.
 */
public record SetUpstreamPopup(Branch.Name branch) implements Popup {}
