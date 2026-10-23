package net.vanfleteren.jazygit.feature.start;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking which of the recent repositories {@code outside} offered to open.
 *
 * @param outside what Escape goes back to
 */
public record OpenRecentPopup(NoRepositoryPopup outside) implements Popup {}
