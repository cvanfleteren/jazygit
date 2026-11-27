package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.git.model.MetadataStamp;
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Reading the reflog of HEAD while its tab is shown (ADR 186): when it is shown, after an operation of the app, after
 * the log is read again, and once what it is read from changed outside the app; a page ahead of the highlighted
 * entry; one read at a time, which finds which of the commits it read are lost as well (ADR 188).
 */
public final class ReflogUpdate {

    private ReflogUpdate() {}

    public static Next update(Model model, ReflogMsg msg) {
        return switch (msg) {
            case ReflogMsg.Read(ReadReflog cmd, Reflog reflog, Option<Kept> kept, Option<MetadataStamp> stamp) ->
                read(model, cmd, reflog, kept, stamp);
            case ReflogMsg.Failed(ReadReflog cmd, Failure failure) -> failed(model, cmd, failure);
            case ReflogMsg.Highlighted(int place) ->
                ahead(model.withPart(
                        ReflogView.PART,
                        view(model)
                                .withHighlighted(Option.of(place))
                                .withReached(Math.max(view(model).reached(), place))));
            case ReflogMsg.Tick() -> looking(model) ? Next.of(model, new StampReflog()) : Next.of(model);
            case ReflogMsg.Stamped(Option<MetadataStamp> stamp) -> stamped(model, stamp);
        };
    }

    /**
     * Whether to look if the reflog changed outside the app: while its tab is shown, it is read, and no read runs.
     * Hidden, it is not looked at, as showing it reads it again anyway.
     */
    private static boolean looking(Model model) {
        ReflogView view = view(model);
        return shown(model) && view.reading().isEmpty() && view.entries() instanceof Loadable.Loaded<?>;
    }

    /**
     * The reflog read again once what it is read from looks other than when it was read, or cannot be told apart.
     */
    private static Next stamped(Model model, Option<MetadataStamp> stamp) {
        return looking(model) && (stamp.isEmpty() || !stamp.equals(view(model).stamp()))
                ? reread(model)
                : Next.of(model);
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
                    KernelEvent.Switched _,
                    KernelEvent.FileToOpen _,
                    KernelEvent.RemoteChanged _ -> Next.of(model);
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
     * {@code model} with what {@code cmd} read, {@code reflog}, and what keeps its commits, {@code kept}: from the top,
     * in place of what was known; a later page, for its own commits. Unknown, nothing is marked of what it read.
     */
    private static Next read(
            Model model, ReadReflog cmd, Reflog reflog, Option<Kept> kept, Option<MetadataStamp> stamp) {
        ReflogView view = view(model);
        if (!view.reading().contains(cmd)) {
            return Next.of(model);
        }
        ReflogView done = view.withReading(Option.none()).withStamp(cmd.skip() == 0 ? stamp : view.stamp());
        return next(
                model,
                switch (reflog) {
                    case Reflog.Empty empty ->
                        done.withEntries(new Loadable.Loaded<>(List.empty()))
                                .withEmpty(Option.of(empty))
                                .withEnded(true)
                                .withKept(Kept.BY_REFS);
                    case Reflog.Entries(List<ReflogEntry> page) ->
                        done.withEntries(done.entries().reload(joined(view, cmd, page)))
                                .withEmpty(Option.none())
                                .withEnded(page.size() < cmd.max())
                                .withKept((cmd.skip() == 0 ? Kept.BY_REFS : view.kept())
                                        .with(kept.getOrElse(Kept.BY_REFS), page.map(ReflogEntry::sha)));
                });
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
