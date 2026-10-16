package net.vanfleteren.jazygit.feature.branch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking what to do with the upstream of the checked out {@code branch}; none when no branch is checked out,
 * or the branches are not loaded yet.
 */
public record UpstreamPopup(Option<Branch> branch) implements Popup {}
