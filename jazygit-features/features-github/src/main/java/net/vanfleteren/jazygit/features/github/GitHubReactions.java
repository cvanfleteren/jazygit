package net.vanfleteren.jazygit.features.github;

import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.PullRequestsUpdate;

/**
 * What GitHub's pull requests and checks do when something happens in the kernel (ADR 158): they are read again as
 * what GitHub has changed, or may have, and the first time once a repository is opened.
 */
final class GitHubReactions {

    private GitHubReactions() {}

    /**
     * What {@code model} becomes, and what is read, now that {@code event} happened.
     */
    static Next to(Model model, KernelEvent event) {
        return switch (event) {
            // Whichever read still runs may have missed what the operation changed there: it is read once more.
            case KernelEvent.OperationDone(boolean changedForge) ->
                changedForge ? PullRequestsUpdate.loadAnyway(Next.of(model)) : Next.of(model);
            // As what GitHub has changes on its own, after each fetch; only those reads that do not keep failing.
            case KernelEvent.Fetched _ -> PullRequestsUpdate.poll(Next.of(model));
            // The pull requests, and the new ones among them (ADR 29), show from the start, not only once a fetch is
            // done (ADR 58).
            case KernelEvent.RepositoryOpened _ -> PullRequestsUpdate.load(Next.of(model));
            case KernelEvent.LogRead _ -> Next.of(model);
        };
    }
}
