package net.vanfleteren.jazygit.state.github;

import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Reading how the CI checks are doing on the branches on GitHub.
 */
public sealed interface BranchChecksMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BranchChecksUpdate.update(model, this);
    }

    record Loaded(GitHubChecks checks) implements BranchChecksMsg {}

    record LoadFailed(Failure failure) implements BranchChecksMsg {}

    /**
     * The checks of the branches whose checks are pending were read one by one, with their timing (ADR 32).
     */
    record RunsLoaded(PendingChecks checks) implements BranchChecksMsg {}

    record RunsFailed(Failure failure) implements BranchChecksMsg {}
}
