package net.vanfleteren.jazygit.feature.cherrypick;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to cherry-pick {@code commit} onto the checked out branch {@code onto}; none when HEAD is detached.
 */
public record CherryPickPopup(Commit commit, Option<Branch.Name> onto) implements Popup {}
