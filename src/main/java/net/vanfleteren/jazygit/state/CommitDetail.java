package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * What the commit highlighted in the commits pane changed: its files, a summary and its diff.
 *
 * @param previous the last fully loaded detail of another commit, kept while this one is loading
 *                 so the UI can keep showing it instead of flashing a placeholder
 */
public record CommitDetail(Sha sha, Loadable<String> changes, Option<CommitDetail> previous) {

    public CommitDetail(Sha sha, Loadable<String> changes) {
        this(sha, changes, Option.none());
    }

    /**
     * A detail that is loading, falling back to the detail that was shown before it.
     */
    public static CommitDetail loading(Sha sha, Option<CommitDetail> shown) {
        Option<CommitDetail> previous = shown.flatMap(detail ->
                detail.changes() instanceof Loadable.Loaded<String> ? Option.of(detail) : detail.previous());
        return new CommitDetail(sha, Loadable.loading(), previous);
    }

    /**
     * Like {@link Loadable#reload}, returns this instance when the changes are unchanged.
     */
    public CommitDetail reload(String changes) {
        Loadable<String> reloaded = this.changes.reload(changes);
        return reloaded == this.changes ? this : new CommitDetail(sha, reloaded);
    }
}
