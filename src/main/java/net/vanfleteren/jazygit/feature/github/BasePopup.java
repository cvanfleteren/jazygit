package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking which branch the pull request of {@code branch} merges into.
 *
 * @param bases the branches to choose from, the default branch first
 */
public record BasePopup(String branch, List<String> bases) implements Popup {
}
