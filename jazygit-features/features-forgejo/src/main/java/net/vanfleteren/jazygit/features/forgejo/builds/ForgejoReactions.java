package net.vanfleteren.jazygit.features.forgejo.builds;

import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What Forgejo's builds do when something happens in the kernel (ADR 158, 174): they are read once Forgejo is found
 * to apply, again after a fetch, and for a push.
 */
public final class ForgejoReactions {

    private ForgejoReactions() {}

    /**
     * What {@code model} becomes, and what is read, now that {@code event} happened.
     */
    public static Next to(Model model, KernelEvent event) {
        return switch (event) {
            case KernelEvent.Switched(String feature, boolean on)
            // Afresh, as the repository may be another one, with other remotes under the same names.
            when feature.equals(ForgejoFeature.ID) && on ->
                BuildsUpdate.read(model.withPart(ForgejoBuilds.PART, ForgejoBuilds.NONE));
            // A build may have run again since a commit was read.
            case KernelEvent.Fetched _ -> BuildsUpdate.read(model.mapPart(ForgejoBuilds.PART, ForgejoBuilds::fetched));
            // A push brings a tip that was not read.
            case KernelEvent.OperationDone(boolean changedForge) ->
                changedForge ? BuildsUpdate.read(model) : Next.of(model);
            case KernelEvent.Switched _,
                    KernelEvent.TabShown _,
                    KernelEvent.RepositoryOpened _,
                    KernelEvent.LogRead _,
                    KernelEvent.ScreenShown _,
                    KernelEvent.ChangesOpened _,
                    KernelEvent.FileToOpen _ -> Next.of(model);
        };
    }
}
