package net.vanfleteren.jazygit.state;

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
     * The checks of the branches whose checks are pending were read one by one, with their timing (ADR 30).
     */
    record RunsLoaded(PendingChecks checks) implements BranchChecksMsg {}

    record RunsFailed(Failure failure) implements BranchChecksMsg {}
}
