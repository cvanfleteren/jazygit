package net.vanfleteren.jazygit.features.github.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Reading the branches that are protected on GitHub; GitHub's feature reads the open pull requests (ADR 168).
 */
public sealed interface PullRequestsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PullRequestsUpdate.update(model, this);
    }

    /**
     * The branches that are protected on GitHub (ADR 72).
     */
    record ProtectedLoaded(List<ForgeBranch> branches) implements PullRequestsMsg {}

    record ProtectedLoadFailed(Failure failure) implements PullRequestsMsg {}
}
