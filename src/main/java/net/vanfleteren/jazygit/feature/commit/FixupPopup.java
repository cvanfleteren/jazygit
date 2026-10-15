package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to fix up {@code commit} into {@code into}, its parent.
 */
public record FixupPopup(Commit commit, Commit into) implements Popup {}
