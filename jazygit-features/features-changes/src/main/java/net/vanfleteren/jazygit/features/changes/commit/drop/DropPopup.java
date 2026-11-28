package net.vanfleteren.jazygit.features.changes.commit.drop;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.popup.ConfirmablePopup;

/**
 * Asking to confirm dropping {@code commit}.
 *
 * @param warning that {@code commit} is pushed already, if it is
 */
public record DropPopup(Commit commit, Option<PushedWarning> warning) implements ConfirmablePopup {

    /**
     * Dropping a commit that is not pushed.
     */
    public DropPopup(Commit commit) {
        this(commit, Option.none());
    }

    @Override
    public Option<DropCmd> confirm() {
        return Option.of(new DropCmd(commit.sha()));
    }
}
