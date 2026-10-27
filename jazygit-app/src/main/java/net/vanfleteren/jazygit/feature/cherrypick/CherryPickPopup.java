package net.vanfleteren.jazygit.feature.cherrypick;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking to cherry-pick {@code commit} onto the checked out branch {@code onto}; none when HEAD is detached.
 */
public record CherryPickPopup(Commit commit, Option<BranchName> onto) implements ConfirmablePopup {
    @Override
    public Option<CherryPickCmd> confirm() {
        return Option.of(new CherryPickCmd(commit.sha()));
    }
}
