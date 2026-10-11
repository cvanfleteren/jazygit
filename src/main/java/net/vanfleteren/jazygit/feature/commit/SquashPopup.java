package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm squashing {@code commit} into {@code into}, its parent.
 */
public record SquashPopup(Commit commit, Commit into) implements Popup {
}
