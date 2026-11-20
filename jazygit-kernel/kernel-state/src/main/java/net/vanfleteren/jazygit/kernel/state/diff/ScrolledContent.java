package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * What an area of the content panel shows, which where it is {@link Scrolled scrolled} to holds for: one of the diffs,
 * which the content panel opens, versions and expands, and so tells apart, or {@link Other other} content, which only
 * its source knows (ADR 155).
 */
public sealed interface ScrolledContent {

    /**
     * Whether {@code other} shows the changes this shows, read again, as after {@code R} or ignoring whitespace:
     * the cursor of a rich diff then stays on its stop (ADR 60).
     */
    default boolean sameChanges(ScrolledContent other) {
        return switch (this) {
            case CommitChanges(Commit commit, var ignored) ->
                other instanceof CommitChanges(Commit same, var also)
                        && same.sha().equals(commit.sha());
            case StashChanges(Sha sha, var ignored) ->
                other instanceof StashChanges(Sha same, var also) && same.equals(sha);
            case FileChanges(Changes of, List<CommitFile> files, var ignored) ->
                other instanceof FileChanges(Changes same, List<CommitFile> those, var also)
                        && same.sameAs(of)
                        && those.map(CommitFile::path).equals(files.map(CommitFile::path));
            default -> equals(other);
        };
    }

    /**
     * The diff this shows, if it is one the rich view draws.
     */
    default Option<DiffText> richDiff() {
        return switch (this) {
            case Diff(DiffText diff) -> Option.of(diff);
            case CommitChanges(var commit, DiffText changes) -> Option.of(changes);
            case StashChanges(var sha, DiffText changes) -> Option.of(changes);
            case FileChanges(var of, var files, DiffText diff) -> Option.of(diff);
            case Unchanged(var path, DiffText asDiff, var commit) -> Option.of(asDiff);
            case Other _ -> Option.none();
        };
    }

    /**
     * A diff of the working tree: the staged or the unstaged changes.
     */
    record Diff(DiffText diff) implements ScrolledContent {}

    /**
     * The file at {@code path} without changes, a diff of it against itself: in the working tree, as it is on disk
     * (ADR 108), or in the commit {@code commit} whose files are listed, as it is in it (ADR 128).
     */
    record Unchanged(String path, DiffText asDiff, Option<Sha> commit) implements ScrolledContent {

        /**
         * The file at {@code path} in the working tree, as it is on disk.
         */
        public Unchanged(String path, DiffText asDiff) {
            this(path, asDiff, Option.none());
        }

        /**
         * How the file is read whole: from disk, or from its commit.
         */
        public WholeFile.Source source() {
            return commit.<WholeFile.Source>map(sha -> new WholeFile.Source.AtCommit(sha, path))
                    .getOrElse(() -> new WholeFile.Source.OnDisk(path));
        }
    }

    /**
     * A commit and what it changed, in the commits pane or in a divergence.
     */
    record CommitChanges(Commit commit, DiffText changes) implements ScrolledContent {}

    /**
     * What the stash {@code sha} stashed. Named by its sha, as its {@code stash@{n}} changes as stashes are added and
     * dropped.
     */
    record StashChanges(Sha sha, DiffText changes) implements ScrolledContent {}

    /**
     * What the commit, the stash or the pull request {@code of} changed in {@code files}.
     */
    record FileChanges(Changes of, List<CommitFile> files, DiffText diff) implements ScrolledContent {}

    /**
     * Content that is no diff, as the log or the builds of a branch, a pull request or a note: a value of the source
     * that shows it, in its own package, which the area keeps where it is scrolled to for while it is {@code equals}
     * (ADR 155). Only its body tells it apart.
     */
    non-sealed interface Other extends ScrolledContent {}
}
