package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import net.vanfleteren.jazygit.core.git.model.DiffFiltering.HunkStart;
import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * The hunks filters hide that the user opened in the diff an area of the content panel shows (ADR 144), each by its
 * path and where it starts in the old file and the new one, as git gave it. They hold as {@link Expansion}s do.
 *
 * @param content what the area showed when the first of them was opened
 * @param hunks   the hunks opened
 */
public record Opened(ScrolledContent content, Set<Hunk> hunks) {

    /**
     * Nothing opened yet in the diff of {@code content}.
     */
    public static Opened of(ScrolledContent content) {
        return new Opened(content, HashSet.empty());
    }

    /**
     * Whether these hold for an area that shows {@code shown}: the same changes, read again or not, and for the
     * working tree any of its diffs.
     */
    public boolean holdsFor(ScrolledContent shown) {
        return content.sameChanges(shown)
                || (content instanceof ScrolledContent.Diff && shown instanceof ScrolledContent.Diff);
    }

    /**
     * Whether the hunk of the file with the path {@code path} that starts at {@code start} is opened.
     */
    public boolean has(RepoPath path, HunkStart start) {
        return hunks.contains(new Hunk(path, start));
    }

    /**
     * A hunk of the file with the path {@code path} that starts at {@code start}.
     */
    public record Hunk(RepoPath path, HunkStart start) {}
}
