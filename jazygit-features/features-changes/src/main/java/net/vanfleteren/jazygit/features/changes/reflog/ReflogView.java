package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.git.model.MetadataStamp;
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.view.TabId;

/**
 * The reflog of HEAD the commits pane shows in a tab (ADR 186): the entries read so far, newest first, a page at a
 * time, and the read that runs, of which there is one at most; which of their commits are lost; and which entry is
 * highlighted.
 *
 * @param entries     the entries read, from the newest on
 * @param empty       why the reflog has no entries, once that is read
 * @param ended       whether the oldest entry is read
 * @param reached     the place of the oldest entry highlighted so far: once it is in the last page read, the next is
 *                    read
 * @param reading     the read that runs, if one does
 * @param again       whether the entries are to be read again once that read is done, as they changed while it ran
 * @param highlighted the place of the entry highlighted, once one is
 * @param kept        what keeps those of the commits of the entries no branch, tag or remote reaches, as far as that
 *                    was found: read with the entries, it is of the entries read (ADR 188)
 * @param stamp       what the reflog was read from looked like when it was last read from the top; none when that
 *                    could not be told, and it is then read again
 */
@With
public record ReflogView(
        Loadable<List<ReflogEntry>> entries,
        Option<Reflog.Empty> empty,
        boolean ended,
        int reached,
        Option<ReadReflog> reading,
        boolean again,
        Option<Integer> highlighted,
        Kept kept,
        Option<MetadataStamp> stamp) {

    /**
     * The tab of the commits pane the reflog is shown in.
     */
    public static final TabId TAB = new TabId("changes.reflog");

    public static final ReflogView NONE = new ReflogView(
            Loadable.loading(),
            Option.none(),
            false,
            0,
            Option.none(),
            false,
            Option.none(),
            Kept.BY_REFS,
            Option.none());

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
     * The entry highlighted, if one is and it is read.
     */
    public Option<ReflogEntry> highlightedEntry() {
        return highlighted.flatMap(place -> entries.getOrElse(List.empty()).find(entry -> entry.place() == place));
    }

    /**
     * Whether nothing but the reflog keeps the commit of {@code entry}, as far as that is known.
     */
    public boolean isLost(ReflogEntry entry) {
        return kept.lost().contains(entry.sha());
    }

    /**
     * Whether HEAD is all that keeps the commit of {@code entry}, as far as that is known: it is lost once HEAD moves
     * away.
     */
    public boolean isKeptByHead(ReflogEntry entry) {
        return kept.byHead().contains(entry.sha());
    }

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
