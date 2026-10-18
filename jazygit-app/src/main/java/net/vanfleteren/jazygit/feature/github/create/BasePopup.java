package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking which branch the pull request of {@code branch} merges into.
 *
 * @param bases the branches to choose from, the default branch first
 */
public record BasePopup(Branch.Name branch, List<Branch.Name> bases) implements Popup {}
