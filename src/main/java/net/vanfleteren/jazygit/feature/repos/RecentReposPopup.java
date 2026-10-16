package net.vanfleteren.jazygit.feature.repos;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking which of the recent repositories to open instead of this one.
 *
 * @param repos the last one opened first
 */
public record RecentReposPopup(List<RecentRepo> repos) implements Popup {}
