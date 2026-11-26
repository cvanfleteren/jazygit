package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.popup.ConfirmablePopup;

/**
 * Asking whether to solve the file at {@code path} with mergiraf though it was edited since git wrote its conflict:
 * mergiraf starts again from that conflict, and the edits are lost (ADR 201).
 */
public record SolveOverEditsPopup(RepoPath path) implements ConfirmablePopup {

    @Override
    public Option<ResolveCmd> confirm() {
        return Option.of(new ResolveCmd.Solve(path, true));
    }
}
