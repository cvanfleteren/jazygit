package net.vanfleteren.jazygit.feature.github.checks;

import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PendingChecks;
import net.vanfleteren.jazygit.state.Update;

/**
 * Showing the checks on the tip of a branch one by one (ADR 32).
 */
public sealed interface ChecksMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ChecksUpdate.update(model, this);
    }

    /**
     * The user asked for the checks of {@code branch}, local or remote.
     */
    record Requested(BranchRef branch) implements ChecksMsg {}

    /**
     * The checks on {@code branch} on GitHub were read.
     */
    record Loaded(RemoteBranch branch, PendingChecks checks) implements ChecksMsg {}

    record Failed(RemoteBranch branch, Failure failure) implements ChecksMsg {}

    /**
     * The user asked to read the checks that are shown again.
     */
    record RefreshChosen() implements ChecksMsg {}

    /**
     * Periodic prompt to read the checks again, while they are shown.
     */
    record Tick() implements ChecksMsg {}
}
