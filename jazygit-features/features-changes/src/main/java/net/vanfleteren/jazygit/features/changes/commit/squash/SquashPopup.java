package net.vanfleteren.jazygit.features.changes.commit.squash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.popup.ConfirmablePopup;

/**
 * Asking to confirm squashing {@code commit} into its parent {@code into}, which need not be among the commits read: the
 * search can find commits older than the log reads.
 */
public record SquashPopup(Commit commit, Sha into) implements ConfirmablePopup {
    @Override
    public Option<SquashCmd> confirm() {
        return Option.of(new SquashCmd(commit.sha(), SquashMode.SQUASH));
    }
}
