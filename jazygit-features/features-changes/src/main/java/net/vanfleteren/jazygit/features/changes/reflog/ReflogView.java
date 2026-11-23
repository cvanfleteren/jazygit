package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.view.TabId;

/**
 * The reflog of HEAD the commits pane shows in a tab (ADR 186): the entries read so far, newest first, a page at a
 * time, and the read that runs, of which there is one at most.
 *
 * @param entries the entries read, from the newest on
 * @param empty   why the reflog has no entries, once that is read
 * @param ended   whether the oldest entry is read
 * @param reached the place of the oldest entry highlighted so far: once it is in the last page read, the next is read
 * @param reading the read that runs, if one does
 * @param again   whether the entries are to be read again once that read is done, as they changed while it ran
 */
@With
public record ReflogView(
        Loadable<List<ReflogEntry>> entries,
        Option<Reflog.Empty> empty,
        boolean ended,
        int reached,
        Option<ReadReflog> reading,
        boolean again) {

    /**
     * The tab of the commits pane the reflog is shown in.
     */
    public static final TabId TAB = new TabId("changes.reflog");

    public static final ReflogView NONE =
            new ReflogView(Loadable.loading(), Option.none(), false, 0, Option.none(), false);

    /**
     * Where the model keeps the reflog. It is not carried to another worktree, which has a HEAD and a reflog of its
     * own; the commits it lists can be acted on as those of the log.
     */
    public static final Part<ReflogView> PART = Part.of("changes.reflog", NONE)
            .knowingCommits((view, sha) -> view.entries()
                    .getOrElse(List.empty())
                    .find(entry -> entry.sha().equals(sha))
                    .map(ReflogEntry::commit));

    /**
     * How many entries are read.
     */
    int read() {
        return entries.getOrElse(List.empty()).size();
    }

    /**
     * Whether the next page is to be read: the oldest entry highlighted is in the last page read, and no read runs.
     */
    boolean wantsMore() {
        return reading.isEmpty()
                && !ended
                && entries instanceof Loadable.Loaded<List<ReflogEntry>>
                && reached >= read() - History.REFLOG_PAGE;
    }
}
