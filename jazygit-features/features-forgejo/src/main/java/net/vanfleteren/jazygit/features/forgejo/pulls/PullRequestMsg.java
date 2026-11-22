package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What happens to the pull requests on Forgejo (ADR 176).
 */
public sealed interface PullRequestMsg extends Msg {

    @Override
    default Next apply(Model model) {
        return PullRequestUpdate.update(model, this);
    }

    /**
     * The open pull requests of {@code home} were read: {@code open}, by the branch they merge, when they could be;
     * why reading them failed, if it did.
     */
    record Listed(RemoteName home, Option<Map<RemoteBranch, ForgejoPullRequest>> open, Option<Failure> failure)
            implements PullRequestMsg {}

    /**
     * The user asked for the pull request of {@code branch}.
     */
    record Requested(BranchName branch) implements PullRequestMsg {}

    /**
     * Read {@code read} of the pull request shown brought {@code result}.
     */
    record Read(long read, Either<Failure, ShownPullRequest> result) implements PullRequestMsg {}

    /**
     * The user chose to open the pull request shown in the browser.
     */
    record OpenChosen() implements PullRequestMsg {}

    /**
     * The user chose to read the pull request shown again.
     */
    record RefreshChosen() implements PullRequestMsg {}
}
