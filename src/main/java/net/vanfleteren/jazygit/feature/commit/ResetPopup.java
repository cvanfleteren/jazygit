package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to reset the checked out branch to {@code target}.
 */
public record ResetPopup(Commit target) implements Popup {}
