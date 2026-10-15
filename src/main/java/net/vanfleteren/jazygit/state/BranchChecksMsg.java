package net.vanfleteren.jazygit.state;

/**
 * Reading how the CI checks are doing on the branches on GitHub.
 */
public sealed interface BranchChecksMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BranchChecksUpdate.update(model, this);
    }

    record Loaded(GitHubChecks checks) implements BranchChecksMsg {
    }

    record LoadFailed(Failure failure) implements BranchChecksMsg {
    }
}
