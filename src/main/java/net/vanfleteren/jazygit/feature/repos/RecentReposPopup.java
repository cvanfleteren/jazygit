package net.vanfleteren.jazygit.feature.repos;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking which of the recent repositories to open instead of this one.
 *
 * @param directories the roots of their working trees, the last one opened first
 */
public record RecentReposPopup(List<String> directories) implements Popup {}
