package net.vanfleteren.jazygit.features.branches.start;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which of the recent repositories {@code outside} offered to open.
 *
 * @param outside what Escape goes back to
 */
public record OpenRecentPopup(NoRepositoryPopup outside) implements Popup {}
