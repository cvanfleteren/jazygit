package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Diffs;
import net.vanfleteren.jazygit.model.FileEntry;

import java.util.List;
import java.util.Optional;

/**
 * The diff of the node highlighted in the files pane.
 *
 * @param files    the changed files under the node; they identify what is shown
 * @param previous the last fully loaded diff of other files, kept while this one is loading
 *                 so the UI can keep showing it instead of flashing a placeholder
 */
public record FileDiff(List<FileEntry> files, Loadable<Diffs> diff, Optional<FileDiff> previous) {

    public FileDiff {
        files = List.copyOf(files);
    }

    public FileDiff(List<FileEntry> files, Loadable<Diffs> diff) {
        this(files, diff, Optional.empty());
    }

    /**
     * A diff that is loading, falling back to the diff that was shown before it.
     */
    static FileDiff loading(List<FileEntry> files, Optional<FileDiff> shown) {
        Optional<FileDiff> previous = shown.flatMap(d ->
                d.diff() instanceof Loadable.Loaded<Diffs> ? Optional.of(d) : d.previous());
        return new FileDiff(files, Loadable.loading(), previous);
    }

    /**
     * Like {@link Loadable#reload}, returns this instance when the diff is unchanged.
     */
    FileDiff reload(Diffs diffs) {
        Loadable<Diffs> reloaded = diff.reload(diffs);
        return reloaded == diff ? this : new FileDiff(files, reloaded);
    }
}
