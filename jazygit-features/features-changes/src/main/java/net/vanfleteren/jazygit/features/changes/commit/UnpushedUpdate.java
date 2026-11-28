package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Keeping which commits of the log of HEAD are not pushed yet (#722): read again in one {@code git rev-list} whenever
 * HEAD, its upstream or the main branch is somewhere else than they were read for; none without an upstream on a
 * remote, where there is nothing to push to.
 */
public final class UnpushedUpdate {

    private UnpushedUpdate() {}

    public static Next update(Model model, UnpushedMsg msg) {
        return switch (msg) {
            case UnpushedMsg.Tick() -> check(model);
            case UnpushedMsg.Read(var tips, var commits) -> {
                Unpushed unpushed = model.part(Unpushed.PART);
                yield Next.of(
                        unpushed.asked().contains(tips)
                                ? model.withPart(
                                        Unpushed.PART,
                                        unpushed.withRead(Option.of(tips)).withCommits(commits))
                                : model);
            }
            // Asked again only once the tips move, so that a read git keeps failing does not run every tick.
            case UnpushedMsg.Failed _ -> Next.of(model);
        };
    }

    /**
     * Reads again once the log is read, a fetch or an operation, as a push, moved a tip.
     */
    public static Next reacting(Model model, KernelEvent event) {
        return switch (event) {
            case KernelEvent.LogRead _,
                    KernelEvent.Fetched _,
                    KernelEvent.OperationDone _,
                    KernelEvent.RepositoryOpened _ -> check(model);
            case KernelEvent.ScreenShown _,
                    KernelEvent.ChangesOpened _,
                    KernelEvent.Switched _,
                    KernelEvent.TabShown _,
                    KernelEvent.FileToOpen _,
                    KernelEvent.RemoteChanged _ -> Next.of(model);
        };
    }

    /**
     * The commits of the log of HEAD not pushed yet, as far as they were read for where HEAD and the tips are now.
     */
    public static Set<Sha> shown(Model model) {
        return tips(model).map(model.part(Unpushed.PART)::of).getOrElse(HashSet.empty());
    }

    /**
     * Reads the commits not pushed for where the tips are now, unless they were asked for already; forgets them
     * when there is nothing to push to.
     */
    static Next check(Model model) {
        Unpushed unpushed = model.part(Unpushed.PART);
        Option<Unpushed.Tips> wanted = tips(model);
        if (unpushed.asked().equals(wanted)) {
            return Next.of(model);
        }
        return wanted.fold(
                () -> Next.of(model.withPart(Unpushed.PART, Unpushed.NONE)),
                tips -> Next.of(model.withPart(Unpushed.PART, unpushed.withAsked(wanted)), new ReadUnpushed(tips)));
    }

    /**
     * Where HEAD, its upstream and the main branch on {@code origin} are, as far as the log and the branches were
     * read; none without a branch checked out that follows one on a remote that was fetched.
     */
    static Option<Unpushed.Tips> tips(Model model) {
        Option<Sha> head =
                model.repo().commits().toOption().flatMap(List::headOption).map(Commit::sha);
        Option<Sha> upstream = model.repo()
                .currentBranch()
                .flatMap(Branch::remoteUpstream)
                .flatMap(branch -> model.repo().remoteBranch(branch))
                .map(RemoteRef::tip);
        Option<Sha> main = PushedCheck.otherMain(model)
                .flatMap(name -> model.repo().remoteBranch(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, name)))
                .map(RemoteRef::tip);
        return head.flatMap(at -> upstream.map(upstreamTip ->
                new Unpushed.Tips(at, List.of(upstreamTip).appendAll(main).distinct())));
    }
}
