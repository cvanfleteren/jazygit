package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.model.Sha;

import java.util.Optional;

/**
 * What the commit highlighted in the commits pane changed: its files, a summary and its diff.
 *
 * @param previous the last fully loaded detail of another commit, kept while this one is loading
 *                 so the UI can keep showing it instead of flashing a placeholder
 */
public record CommitDetail(Sha sha, Loadable<String> changes, Optional<CommitDetail> previous) {

    public CommitDetail(Sha sha, Loadable<String> changes) {
        this(sha, changes, Optional.empty());
    }

    /**
     * A detail that is loading, falling back to the detail that was shown before it.
     */
    public static CommitDetail loading(Sha sha, Optional<CommitDetail> shown) {
        Optional<CommitDetail> previous = shown.flatMap(detail ->
                detail.changes() instanceof Loadable.Loaded<String> ? Optional.of(detail) : detail.previous());
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
