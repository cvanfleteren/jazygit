package net.vanfleteren.jazygit.features.changes.commit.reword;

import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Warning that {@code commit} is pushed already, before asking for its new message.
 */
public record RewordPushedPopup(Commit commit, PushedWarning warning) implements Popup {}
