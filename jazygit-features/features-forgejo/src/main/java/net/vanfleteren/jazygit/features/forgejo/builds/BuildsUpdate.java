package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What reading the builds on Forgejo does to the model (ADR 174).
 */
public final class BuildsUpdate {

    /**
     * How many commits are read at a time.
     */
    static final int AT_A_TIME = 20;

    private BuildsUpdate() {}

    static Next update(Model model, BuildsMsg msg) {
        return switch (msg) {
            case BuildsMsg.Tick() -> read(model);
            case BuildsMsg.Read(var tips, var states) ->
                Next.of(model.mapPart(ForgejoBuilds.PART, builds -> builds.read(tips, states)));
        };
    }

    /**
     * {@code model}, reading the commits that are due, at most {@link #AT_A_TIME}: those of the branches the local
     * branches follow first, then those of the other remote branches.
     */
    public static Next read(Model model) {
        ForgejoBuilds builds = model.part(ForgejoBuilds.PART);
        List<Tip> due = builds.due(tips(model)).take(AT_A_TIME);
        return due.isEmpty()
                ? Next.of(model)
                : Next.of(model.withPart(ForgejoBuilds.PART, builds.reading(due)), new ReadBuilds(due));
    }

    /**
     * The tips of the remote branches, once each: those the local branches follow first, in the order of the local
     * branches, then the others.
     */
    static List<Tip> tips(Model model) {
        List<RemoteRef> remote = model.repo().remoteBranches().getOrElse(List.empty());
        List<RemoteRef> followed = model.repo()
                .branches()
                .getOrElse(List.empty())
                .flatMap(Branch::remoteUpstream)
                .flatMap(upstream -> remote.find(ref -> ref.branch().equals(upstream)));
        return followed.appendAll(remote)
                .map(ref -> new Tip(ref.branch().remote(), ref.tip()))
                .distinctBy(Tip::sha);
    }
}
