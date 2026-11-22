package net.vanfleteren.jazygit.features.forgejo.badges;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchBadges;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoBuilds;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * How the last build of a branch on Forgejo went, as the branches pane shows it among its badges (ADR 167, 174): that
 * of the tip of the branch a local branch follows, or of a remote branch's own; nothing while it is not read, or for
 * a commit without builds.
 */
public final class ForgejoBadge {

    private ForgejoBadge() {}

    /**
     * What a row shows of {@code ref} in {@code model}.
     */
    public static BranchBadges.Shown of(Model model, BranchRef ref) {
        ForgejoBuilds builds = model.part(ForgejoBuilds.PART);
        return tip(model, ref)
                .flatMap(builds::state)
                .flatMap(BuildSymbols::of)
                .map(seg -> BranchBadges.Shown.of(List.of(seg)))
                .getOrElse(BranchBadges.Shown.NONE);
    }

    private static Option<Sha> tip(Model model, BranchRef ref) {
        return switch (ref) {
            case BranchRef.Local local ->
                model.repo()
                        .branch(local.branch())
                        .flatMap(Branch::remoteUpstream)
                        .flatMap(upstream -> model.repo().remoteBranch(upstream))
                        .map(RemoteRef::tip);
            case BranchRef.Remote remote ->
                model.repo().remoteBranch(remote.branch()).map(RemoteRef::tip);
        };
    }
}
