package net.vanfleteren.jazygit.feature.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm a hard reset to {@code target}, which discards the uncommitted changes.
 *
 * @param files how many files have changes that are discarded; none when the status is not known
 */
public record HardResetPopup(Commit target, Option<Integer> files) implements Popup {}
