package net.vanfleteren.jazygit.feature.commit.drop;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm dropping {@code commit}.
 */
public record DropPopup(Commit commit) implements Popup {}
