package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import lombok.With;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.Collapsed;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.diff.StatedRemarks;
import net.vanfleteren.jazygit.lib.lens.Lens;

/**
 * The files a commit, a stash, a pull request or a branch changed, which the files pane lists as a tree in place of
 * the changed files of the working tree while it is open (ADR 44, 55, 61).
 *
 * @param of       the commit, the stash, the pull request or the branch
 * @param pane     the id of the pane it was opened from, which gets the focus back when it is closed
 * @param listedIn the id of the pane that lists the files: when another pane gets the focus, it closes
 * @param files    the files, as they are read
 * @param diff     what changed in the files of the node highlighted in the tree, keyed by them, once one is
 *                 highlighted
 * @param listed   which of the files are listed: all of them, or only those with remark threads (ADR 57); the
 *                 remarks are stated by a part, not kept here (ADR 171)
 * @param collapsed which remark threads and remarks are collapsed (ADR 62)
 * @param filter   the filter of the files: the tree shows only those listed that match it (ADR 105)
 * @param allFiles whether the tree lists all files of the commit the files end at, not only those changed (ADR 128)
 * @param tree     all files of that commit, the changed ones as they changed and the others unchanged, once they were
 *                 asked for: they are read once
 * @param unchanged the node highlighted while all files are listed, when nothing under it changed: the content panel
 *                  shows it in place of a diff (ADR 128)
 * @param followed whether they follow the highlight of the list that has the focus, rather than being asked for with
 *                 {@code F}: they are then listed without the focus moving into them (ADR 141)
 * @param whole    whether the files were asked for whole, with Enter, though they follow the highlight and are more
 *                 than {@link #FOLLOWED_LIMIT} (ADR 141)
 * @param before   the files listed before, read, which the pane keeps listing while these are read, until a moment:
 *                 most are read before then, and the pane does not flash a placeholder for them (ADR 141)
 * @param wanted   the path of the file to highlight once the files are listed, in place of the first directory with
 *                 changes: the file of a line blamed on the commit whose files these are (ADR 147)
 */
@With
public record CommitFilesView(
        Changes of,
        PaneId pane,
        PaneId listedIn,
        Loadable<List<CommitFile>> files,
        Option<Preview<List<CommitFile>, DiffText>> diff,
        Listed listed,
        Collapsed collapsed,
        Option<FileFilter> filter,
        boolean allFiles,
        Option<Loadable<List<CommitFile>>> tree,
        Option<UnchangedNode> unchanged,
        boolean followed,
        boolean whole,
        Option<Before> before,
        Option<String> wanted) {

    /**
     * How many files that follow the highlight are listed as a tree: more are counted, and listed once asked for.
     */
    public static final int FOLLOWED_LIMIT = 5000;

    /**
     * How long the files listed before stay listed while the next are read, before the pane says they are read.
     */
    public static final Duration KEPT_WHILE_READ = Duration.ofMillis(200);

    /**
     * The view of the files of {@code of}, opened from the pane {@code pane} and listed in {@code listedIn}, while
     * they are read.
     */
    public static CommitFilesView of(Changes of, PaneId pane, PaneId listedIn) {
        return new CommitFilesView(
                of,
                pane,
                listedIn,
                Loadable.loading(),
                Option.none(),
                Listed.ALL,
                Collapsed.NONE,
                Option.none(),
                false,
                Option.none(),
                Option.none(),
                false,
                false,
                Option.none(),
                Option.none());
    }

    /**
     * The files the tree shows: all files of the commit while they are listed, read or not, else the changed ones
     * (ADR 128).
     */
    public Loadable<List<CommitFile>> shownFiles() {
        return allFiles ? tree.getOrElse(Loadable::loading) : files;
    }

    /**
     * These listing all files of the commit when {@code all}, else the changed ones; the node without changes shown
     * goes with all files. Whether all files are still to be read is for the caller to say, by {@link #tree}.
     */
    public CommitFilesView listingAll(boolean all) {
        return withAllFiles(all).withUnchanged(all ? unchanged : Option.none());
    }

    /**
     * {@code changed}, the files the changes end at changed, with the others of {@code paths}, all files of the commit
     * they end at, as unchanged: those changed keep how, also those that are not in it, as a file the commit deleted;
     * sorted by path (ADR 128).
     */
    public static List<CommitFile> merged(List<CommitFile> changed, List<String> paths) {
        io.vavr.collection.Set<String> touched = changed.map(CommitFile::path).toSet();
        return changed.appendAll(paths.filter(path -> !touched.contains(path))
                        .map(path -> new CommitFile(path, ChangeType.UNCHANGED)))
                .sortBy(CommitFile::path);
    }

    /**
     * The files the tree lists, of those read: all of them, or those with the remark threads {@link #listed} keeps,
     * of those a part states for these files in {@code model}, once those are read too (ADR 57, 171).
     */
    public Loadable<List<CommitFile>> listedFiles(Model model) {
        return listedFiles(model.remarks(of));
    }

    /**
     * The files the tree lists, of those read, with {@code remarks} those a part states for them (ADR 57, 171).
     */
    public Loadable<List<CommitFile>> listedFiles(Option<StatedRemarks> remarks) {
        Loadable<List<CommitFile>> shown = shownFiles();
        if (listed == Listed.ALL || !(shown instanceof Loadable.Loaded<List<CommitFile>>(List<CommitFile> read))) {
            return shown;
        }
        return remarks.map(StatedRemarks::threads)
                .getOrElse(() -> new Loadable.Loaded<>(List.empty()))
                .map(threads -> read.filter(file -> listed.keeps(file, threads)));
    }

    /**
     * The view of the files of {@code of}, while it is the one open.
     */
    public static Lens<RepoView, CommitFilesView> open(Changes of) {
        return RepoView.FILES.andThen(Lens.where(
                FilesView::commitFiles,
                FilesView::withCommitFiles,
                view -> view.of().equals(of)));
    }

    /**
     * How many files these hold back, when they follow the highlight and are more than {@link #FOLLOWED_LIMIT}: the
     * pane counts them in place of their tree until they are asked for whole, as building it at each move would cost
     * a frame and more. All files (ADR 128) and those {@code F} asked for are listed whatever their number (ADR 141).
     */
    public Option<Integer> held() {
        return files.toOption()
                .map(List::size)
                .filter(count -> followed && !whole && !allFiles && count > FOLLOWED_LIMIT);
    }

    /**
     * These, to be listed in place of {@code shown}, the files listed until now, at {@code now}: those stay listed while
     * these are read, until {@link #KEPT_WHILE_READ} from now, or the files those kept in their place, until their
     * moment, while they were still read.
     */
    public CommitFilesView after(Option<CommitFilesView> shown, Instant now) {
        return withBefore(shown.flatMap(view -> view.files() instanceof Loadable.Loaded<List<CommitFile>>
                ? Option.of(new Before(view.withBefore(Option.none()), now.plus(KEPT_WHILE_READ)))
                : view.before()));
    }

    /**
     * What the pane lists at {@code now}: the files listed before while these are read, until their moment, else these.
     */
    public CommitFilesView shown(Instant now) {
        return files instanceof Loadable.Loading<List<CommitFile>>
                ? before.filter(kept -> now.isBefore(kept.until()))
                        .map(Before::view)
                        .getOrElse(this)
                : this;
    }

    /**
     * The files listed before, kept while the next are read, until {@code until}.
     */
    public record Before(CommitFilesView view, Instant until) {}

    /**
     * Which of the files the tree lists (ADR 57).
     */
    public enum Listed {
        /**
         * Every file.
         */
        ALL,
        /**
         * The files with a remark thread that is not resolved.
         */
        UNRESOLVED,
        /**
         * The files with a remark thread, resolved or not.
         */
        REMARKED;

        /**
         * What is listed after this, as {@code c} cycles through them.
         */
        public Listed next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /**
         * Whether {@code file} is listed, with {@code threads} the remark threads of the change.
         */
        public boolean keeps(CommitFile file, List<RemarkThread> threads) {
            return this == ALL
                    || threads.exists(thread ->
                            (this == REMARKED || !thread.resolved()) && thread.isOn(file.path(), file.origin()));
        }
    }
}
