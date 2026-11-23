package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Reading the reflog of HEAD while its tab is shown (ADR 186): when it is shown, after an operation of the app and
 * after the log is read again; a page ahead of the highlighted entry; one read at a time; and after each read, which
 * of the commits it read are lost.
 */
public final class ReflogUpdate {

    private ReflogUpdate() {}

    public static Next update(Model model, ReflogMsg msg) {
        return switch (msg) {
            case ReflogMsg.Read(ReadReflog cmd, Reflog reflog) -> read(model, cmd, reflog);
            case ReflogMsg.Failed(ReadReflog cmd, Failure failure) -> failed(model, cmd, failure);
            case ReflogMsg.Highlighted(int place) ->
                ahead(model.withPart(
                        ReflogView.PART,
                        view(model)
                                .withHighlighted(Option.of(place))
                                .withReached(Math.max(view(model).reached(), place))));
            case ReflogMsg.LostFound(FindLost cmd, Set<Sha> lost) ->
                Next.of(model.withPart(ReflogView.PART, view(model).withLostFound(cmd, lost)));
            case ReflogMsg.LostUnknown _ -> Next.of(model);
        };
    }

    /**
     * What the reflog does once {@code event} happened: it is read when its tab is shown, and again then after an
     * operation, after the log was read, and in another worktree opened. Hidden, it reads nothing.
     */
    public static Next reacting(Model model, KernelEvent event) {
        return switch (event) {
            case KernelEvent.TabShown(var pane, var tab)
            when pane.equals(CommitsPanel.PANE) && tab.equals(ReflogView.TAB) -> reread(model);
            case KernelEvent.LogRead _, KernelEvent.OperationDone _, KernelEvent.RepositoryOpened _ ->
                shown(model) ? reread(model) : Next.of(model);
            case KernelEvent.TabShown _,
                    KernelEvent.Fetched _,
                    KernelEvent.ScreenShown _,
                    KernelEvent.ChangesOpened _,
                    KernelEvent.Switched _ -> Next.of(model);
        };
    }

    /**
     * Whether the commits pane of {@code model} shows the reflog.
     */
    public static boolean shown(Model model) {
        return model.repoView().tab(CommitsPanel.PANE).contains(ReflogView.TAB);
    }

    /**
     * Reads the reflog from the top, as many entries as are read already and a page at least, in one process; once
     * the read that runs is done, when one does.
     */
    static Next reread(Model model) {
        ReflogView view = view(model);
        if (view.reading().isDefined()) {
            return Next.of(model.withPart(ReflogView.PART, view.withAgain(true)));
        }
        ReadReflog cmd = new ReadReflog(0, Math.max(view.read(), History.REFLOG_PAGE));
        return Next.of(model.withPart(ReflogView.PART, view.withReading(Option.of(cmd))), cmd);
    }

    /**
     * {@code model} with what {@code cmd} read, {@code reflog}, then a look for which of the commits it read are lost,
     * once they are drawn, beside what reads next.
     */
    private static Next read(Model model, ReadReflog cmd, Reflog reflog) {
        ReflogView view = view(model);
        if (!view.reading().contains(cmd)) {
            return Next.of(model);
        }
        ReflogView done = view.withReading(Option.none()).withReads(view.reads() + 1);
        return switch (reflog) {
            case Reflog.Empty empty ->
                next(
                        model,
                        done.withEntries(new Loadable.Loaded<>(List.empty()))
                                .withEmpty(Option.of(empty))
                                .withEnded(true));
            case Reflog.Entries(List<ReflogEntry> page) -> {
                Next next = next(
                        model,
                        done.withEntries(done.entries().reload(joined(view, cmd, page)))
                                .withEmpty(Option.none())
                                .withEnded(page.size() < cmd.max()));
                yield page.isEmpty()
                        ? next
                        : new Next(
                                next.model(),
                                next.cmds()
                                        .prepend(new FindLost(
                                                done.reads(), cmd.skip() == 0, page.map(ReflogEntry::sha))),
                                next.quit());
            }
        };
    }

    /**
     * The entries read before {@code cmd}, with the page it read after them: from the top, a page in place of all.
     */
    private static List<ReflogEntry> joined(ReflogView view, ReadReflog cmd, List<ReflogEntry> page) {
        return view.entries().getOrElse(List.empty()).take(cmd.skip()).appendAll(page);
    }

    /**
     * A read that failed: from the top, what was read stays, or the failure shows; a later page ends the reading, so
     * that the highlight asks for it no more.
     */
    private static Next failed(Model model, ReadReflog cmd, Failure failure) {
        ReflogView view = view(model);
        if (!view.reading().contains(cmd)) {
            return Next.of(model);
        }
        ReflogView done = view.withReading(Option.none());
        return next(model, cmd.skip() == 0 ? done.withEntries(done.entries().failed(failure)) : done.withEnded(true));
    }

    /**
     * {@code model} with {@code view}, reading again if that was asked for while it read, else a page ahead; nothing
     * once the tab is hidden, as showing it again reads it from the top.
     */
    private static Next next(Model model, ReflogView view) {
        Model read = model.withPart(ReflogView.PART, view.withAgain(false));
        if (!shown(read)) {
            return Next.of(read);
        }
        return view.again() ? reread(read) : ahead(read);
    }

    /**
     * Reads the next page, if the highlighted entry is in the last one read.
     */
    private static Next ahead(Model model) {
        ReflogView view = view(model);
        if (!view.wantsMore()) {
            return Next.of(model);
        }
        ReadReflog cmd = new ReadReflog(view.read(), History.REFLOG_PAGE);
        return Next.of(model.withPart(ReflogView.PART, view.withReading(Option.of(cmd))), cmd);
    }

    private static ReflogView view(Model model) {
        return model.part(ReflogView.PART);
    }
}
