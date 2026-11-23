package net.vanfleteren.jazygit.features.github;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.github.prs.PullRequestReads;
import net.vanfleteren.jazygit.features.github.remarks.RemarksUpdate;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Reactions.Reaction;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What GitHub's pull requests and checks do when something happens in the kernel (ADR 158): they are read again as
 * what GitHub has changed, or may have, and the first time once GitHub is found to apply to the repository opened.
 */
final class GitHubReactions {

    /**
     * The reaction: to what happens in the kernel, as {@link #to} answers it, and to the commands that start, among
     * which a read of the open pull requests gets the id of a read of GitHub (ADR 119).
     */
    static final Reaction REACTION = new Reaction() {

        @Override
        public Next to(Model model, KernelEvent event) {
            return GitHubReactions.to(model, event);
        }

        @Override
        public Model started(Model model, List<Cmd> cmds) {
            return model.mapPart(GitHubState.PART, gitHub -> gitHub.withStarted(cmds));
        }
    };

    private GitHubReactions() {}

    /**
     * What {@code model} becomes, and what is read, now that {@code event} happened.
     */
    static Next to(Model model, KernelEvent event) {
        return switch (event) {
            // Whichever read still runs may have missed what the operation changed there: it is read once more.
            case KernelEvent.OperationDone(boolean changedForge) ->
                changedForge ? PullRequestReads.loadAnyway(Next.of(model)) : Next.of(model);
            // As what GitHub has changes on its own, after each fetch; only those reads that do not keep failing.
            case KernelEvent.Fetched _ -> PullRequestReads.poll(Next.of(model));
            // The pull requests, and the new ones among them (ADR 29), show from the start, not only once a fetch is
            // done (ADR 58): from the moment GitHub is found to apply to the repository (ADR 171).
            case KernelEvent.Switched(String feature, boolean on)
            when feature.equals(GitHubFeature.ID) && on -> PullRequestReads.load(Next.of(model));
            case KernelEvent.Switched _, KernelEvent.RepositoryOpened _, KernelEvent.TabShown _ -> Next.of(model);
            // The review threads of a pull request whose files were opened, and those collapsed (ADR 55, 63, 171).
            case KernelEvent.ChangesOpened(var changes) -> RemarksUpdate.opened(model, changes);
            case KernelEvent.LogRead _, KernelEvent.ScreenShown _ -> Next.of(model);
        };
    }
}
