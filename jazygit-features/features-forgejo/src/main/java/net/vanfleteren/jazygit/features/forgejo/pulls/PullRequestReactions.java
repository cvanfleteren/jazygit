package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What the pull requests on Forgejo do when something happens in the kernel (ADR 158, 176): they are read once
 * Forgejo is found to apply, again after a fetch, as one may have been opened or merged since, and after a push.
 */
public final class PullRequestReactions {

    private PullRequestReactions() {}

    /**
     * What {@code model} becomes, and what is read, now that {@code event} happened.
     */
    public static Next to(Model model, KernelEvent event) {
        return switch (event) {
            case KernelEvent.Switched(FeatureId feature, boolean on)
            when feature.equals(ForgejoFeature.ID) && on -> PullRequestUpdate.turnedOn(model);
            case KernelEvent.Fetched _ -> PullRequestUpdate.list(model);
            case KernelEvent.OperationDone(boolean changedForge) ->
                changedForge ? PullRequestUpdate.list(model) : Next.of(model);
            case KernelEvent.Switched _,
                    KernelEvent.TabShown _,
                    KernelEvent.RepositoryOpened _,
                    KernelEvent.LogRead _,
                    KernelEvent.ScreenShown _,
                    KernelEvent.ChangesOpened _,
                    KernelEvent.FileToOpen _,
                    KernelEvent.RemoteChanged _ -> Next.of(model);
        };
    }
}
