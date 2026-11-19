package net.vanfleteren.jazygit.ui.files;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ThreadsByPath;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.state.view.Followed;
import net.vanfleteren.jazygit.state.view.Unlisted;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.text.ShownTime;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.BorderBar;
import net.vanfleteren.jazygit.ui.widgets.FilterLine;
import net.vanfleteren.jazygit.ui.widgets.Pane;
import net.vanfleteren.jazygit.ui.widgets.PaneKeys;
import net.vanfleteren.jazygit.ui.widgets.PasteTarget;

/**
 * Left-side panel showing the changed files, as in {@code git status}, as a directory tree; or, while they are open,
 * the files a commit or a stash changed (ADR 44). Each tree keeps its own selection and expansion, so the files of the
 * working tree are as they were when those of a commit are closed. The files of a commit open on the first directory
 * that holds files directly, so its diff shows at once. {@code /} filters the tree shown by the paths of its files
 * (ADR 105): the line above it takes the keys that edit the filter, and Escape clears it before it does anything else.
 * {@code a} lists all files of the working tree in place of its changed ones (ADR 108), in a tree of its own that opens
 * only on the directories with changes; in the files of a commit, all files of that commit (ADR 128), the same way.
 */
public class FilesPanel {

    public static final String ID = Scopes.FILES.id();

    /**
     * The pane, as the focus is on it.
     */
    public static final PaneId PANE = Scopes.FILES.pane();

    private final FileTreeList<FileEntry> statusTree;
    // All files of the working tree, kept apart from its changed files, so that each keeps its highlight and the
    // directories collapsed in it as the files pane switches between them (ADR 108).
    private final FileTreeList<FileEntry> allTree;
    private final FileTreeList<CommitFile> commitTree;
    // All files of the commit whose files are listed, kept apart from those it changed, as for the working tree
    // (ADR 128).
    private final FileTreeList<CommitFile> commitAllTree;
    private final Keymap keymap = Keymap.standard();
    private final FilterLine filterLine = new FilterLine();
    // What a paste does: while the filter line is open, it goes in it.
    private final Consumer<PasteEvent> paste;
    // The trees' key handlers also see keys typed in other panes, so they must know whether the pane has focus.
    private boolean focused;
    // Whether the files of a commit the pane lists are counted in place of their tree, as there are many (ADR 141).
    private boolean held;
    // All files of the working tree, as last merged, with the status and the paths they were merged from: merging
    // them again on every frame would cost tens of milliseconds in a repository of 100 000 files (ADR 108).
    private Option<Merged> merged = Option.none();
    // How many files the directory without changes highlighted last holds, the node itself the key: counting them on
    // every frame would walk all files under the root.
    private Option<Counted> counted = Option.none();
    // The filter of the tree shown, as last rendered (ADR 105).
    private Option<FileFilter> filter = Option.none();
    // The review threads of the pull request whose files are listed, by their path, as last rendered (ADR 55).
    private ThreadsByPath threads = ThreadsByPath.of(List.empty());

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     * @param openDiff called when Enter or Right is pressed on a file or directory, to move focus to its diff
     */
    public FilesPanel(Consumer<Msg> dispatch, Runnable openDiff) {
        this(dispatch, openDiff, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted node is shown, also when the pane is not in use
     */
    public FilesPanel(Consumer<Msg> dispatch, Runnable openDiff, Theme.Highlights highlights) {
        paste = event -> filter.filter(FileFilter::typing)
                .forEach(typing -> dispatch.accept(new FileFilterMsg.Edited(filterLine.paste(typing.text(), event))));
        statusTree = new FileTreeList<>(
                ID,
                file -> file.entry().type().marker() + " " + name(file),
                FilesPanel::drawEntry,
                highlights,
                FileTreeList::first);
        allTree = new FileTreeList<>(
                ID,
                file -> file.entry().type().marker() + " " + name(file),
                FilesPanel::drawEntry,
                highlights,
                FileTreeList::first,
                FilesPanel::unchangedDirectories);
        commitTree = new FileTreeList<>(
                ID,
                file -> file.entry().type().marker() + " " + name(file),
                this::drawCommitFile,
                highlights,
                FileTreeList::firstWithDiff);
        commitAllTree = new FileTreeList<>(
                ID,
                file -> file.entry().type().marker() + " " + name(file),
                this::drawCommitFile,
                highlights,
                FileTreeList::firstWithDiff,
                files -> unchangedDirectories(files, FilesPanel::changed));
        List.of(statusTree, allTree)
                .forEach(tree -> tree.onKey(PaneKeys.whileFocused(
                        () -> focused,
                        event -> filtered(event, dispatch)
                                .orElse(() -> opensDiff(event, tree)
                                        ? Option.of(openDiff)
                                        : PaneKeys.sending(
                                                keymap.handle(Scopes.FILES, event, tree.selected()), dispatch)))));
        List.of(commitTree, commitAllTree)
                .forEach(tree -> tree.onKey(PaneKeys.whileFocused(
                        () -> focused,
                        event -> filtered(event, dispatch)
                                // Enter lists the files counted in place of their tree (ADR 141).
                                .orElse(() -> held && event.isConfirm()
                                        ? Option.of(() -> dispatch.accept(new SelectionMsg.WholeListed()))
                                        : Option.none())
                                .orElse(() -> opensDiff(event, tree)
                                        ? Option.of(openDiff)
                                        : PaneKeys.sending(
                                                keymap.handle(Scopes.COMMIT_FILES, event, tree.selected()),
                                                dispatch)))));
    }

    /**
     * What {@code event} does to the filter of the tree shown (ADR 105). While its line is open, the keys that edit
     * it do, Enter keeps it and Escape puts back the one from before; the keys the tree moves with go to the tree,
     * and the others do nothing. Once it is closed, Escape clears the filter, before it does anything else.
     */
    private Option<Runnable> filtered(KeyEvent event, Consumer<Msg> dispatch) {
        return filter.filter(FileFilter::typing)
                .map(typing -> moves(event)
                        ? Option.<Runnable>none()
                        : Option.<Runnable>of(() -> typed(typing.text(), event).forEach(dispatch)))
                .getOrElse(() -> filter.isDefined() && event.isCancel()
                        ? Option.of(() -> dispatch.accept(new FileFilterMsg.Cleared()))
                        : Option.none());
    }

    /**
     * What {@code event} sends while the line holds {@code text}.
     */
    private Option<Msg> typed(String text, KeyEvent event) {
        return event.isCancel()
                ? Option.of(new FileFilterMsg.Cancelled())
                : event.isConfirm()
                        ? Option.of(new FileFilterMsg.Submitted())
                        : filterLine.edit(text, event).map(FileFilterMsg.Edited::new);
    }

    /**
     * Whether {@code event} is one of the keys the tree moves its highlight with, which it keeps while the filter is
     * typed.
     */
    private static boolean moves(KeyEvent event) {
        return event.matches(Actions.MOVE_UP)
                || event.matches(Actions.MOVE_DOWN)
                || event.matches(Actions.PAGE_UP)
                || event.matches(Actions.PAGE_DOWN)
                || event.code() == KeyCode.HOME
                || event.code() == KeyCode.END;
    }

    /**
     * Whether {@code event} moves the focus to the diff of what {@code tree} has highlighted: Enter does, and so does
     * Right, except on a collapsed directory, which it expands as in any tree.
     */
    private static boolean opensDiff(KeyEvent event, FileTreeList<?> tree) {
        return tree.selected().isDefined()
                && (event.isConfirm() || (event.matches(Actions.MOVE_RIGHT) && !tree.collapsedSelected()));
    }

    /**
     * Files show two status columns like {@code git status --short}: the staged change in green, then
     * the unstaged change in red. Untracked files show a red {@code ??}. Files in a merge conflict show a
     * {@code U} for each branch that updated them: green for the current branch, red for the other.
     */
    private static StyledElement<?> drawEntry(FileTree.File<FileEntry> file) {
        FileEntry entry = file.entry();
        // A file without changes, among all files, has no marker: its name lines up with those that have one.
        if (!entry.changed()) {
            return Toolkit.text("   " + file.name());
        }
        if (entry.type() == ChangeType.UNTRACKED) {
            return Toolkit.row(Toolkit.text("??").addClass(Theme.Classes.UNSTAGED), Toolkit.text(" " + file.name()));
        }
        String staged = entry.conflict()
                .map(conflict -> marker(conflict.ours()))
                .getOrElse(() -> entry.staged() ? entry.type().marker() : " ");
        String unstaged = entry.conflict()
                .map(conflict -> marker(conflict.theirs()))
                .getOrElse(() -> entry.unstaged() ? unstagedMarker(entry) : " ");
        return Toolkit.row(
                Toolkit.text(staged).addClass(Theme.Classes.STAGED),
                Toolkit.text(unstaged).addClass(Theme.Classes.UNSTAGED),
                Toolkit.text(" " + name(file)));
    }

    /**
     * The files of a commit show the letter of their change, in the colour of a staged change; those of a pull
     * request after that how many unresolved review threads they have, if any (ADR 55), and how many resolved ones,
     * dimmed (ADR 57).
     */
    private StyledElement<?> drawCommitFile(FileTree.File<CommitFile> file) {
        // A file the commit did not change, among all its files, has no marker: its name lines up with the others.
        if (!changed(file.entry())) {
            return Toolkit.text("  " + file.name());
        }
        List<ReviewThread> on = threads.on(file.entry().path(), file.entry().origin());
        int resolved = on.count(ReviewThread::resolved);
        return Toolkit.row(List.<StyledElement<?>>of(
                        Toolkit.text(file.entry().type().marker()).addClass(Theme.Classes.STAGED),
                        Toolkit.text(" " + name(file)))
                .appendAll(Option.of(on.size() - resolved)
                        .filter(count -> count > 0)
                        .map(count -> Toolkit.text(" 💬" + count).addClass(Theme.Classes.DIFF_THREAD)))
                .appendAll(Option.of(resolved)
                        .filter(count -> count > 0)
                        .map(count -> Toolkit.text(" ✓" + count)
                                .addClass(Theme.Classes.DIFF_THREAD)
                                .dim()))
                .toJavaArray(StyledElement[]::new));
    }

    /**
     * The key of what the title of the files of a pull request says after it when only some of them are listed.
     */
    private static Option<String> listedTitle(CommitFilesView.Listed listed) {
        return switch (listed) {
            case ALL -> Option.none();
            case UNRESOLVED -> Option.of("panel.commitFiles.unresolved");
            case REMARKED -> Option.of("panel.commitFiles.remarked");
        };
    }

    /**
     * What the tree of {@code view} says while its files are read, and when none is listed: once they are read, only
     * some are listed while the review threads are read, and it says why none is when only some are (ADR 57).
     */
    private static FileTreeList.Placeholder placeholder(CommitFilesView view) {
        return new FileTreeList.Placeholder(
                view.shownFiles() instanceof Loadable.Loaded<?>
                        ? Messages.get("panel.commitFiles.readingRemarks")
                        : Placeholders.loading(),
                nothingListed(view.listed()));
    }

    /**
     * Why none of the files of a pull request is listed, when only some of them are.
     */
    public static Option<String> nothingListed(CommitFilesView.Listed listed) {
        return switch (listed) {
            case ALL -> Option.none();
            case UNRESOLVED -> Option.of(Messages.get("panel.commitFiles.noneUnresolved"));
            case REMARKED -> Option.of(Messages.get("panel.commitFiles.noneRemarked"));
        };
    }

    /**
     * The marker of what changed in the working tree: a deletion, a rename made only there (after intent-to-add
     * of the new path), or else a modification.
     */
    private static String unstagedMarker(FileEntry entry) {
        return switch (entry.type()) {
            case DELETED -> "D";
            case RENAMED -> entry.staged() ? "M" : "R";
            default -> "M";
        };
    }

    /**
     * The file name, preceded by where a renamed file came from: just the name when it stayed in the same
     * directory, the full path otherwise.
     */
    private static String name(FileTree.File<?> file) {
        String path = file.entry().path();
        String directory = path.substring(0, path.length() - file.name().length());
        return file.entry()
                .origin()
                .map(from -> from.startsWith(directory) ? from.substring(directory.length()) : from)
                .map(from -> from + " -> " + file.name())
                .getOrElse(file.name());
    }

    /**
     * {@code U} for a branch that updated the conflicting file, {@code D} for one that deleted it.
     */
    private static String marker(Conflict.Side side) {
        return side == Conflict.Side.DELETED ? "D" : "U";
    }

    /**
     * When what the pane lists in {@code model} changes as time goes by (ADR 124): the files listed before stop being
     * listed in place of those still read, which the pane then says it reads (ADR 141).
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.repoView()
                .files()
                .commitFiles()
                .filter(view -> view.files() instanceof Loadable.Loading<List<CommitFile>>)
                .flatMap(CommitFilesView::before)
                .map(kept -> (ShownTime) (now, before) ->
                        Option.of(kept.until()).filter(until -> until.isAfter(now) && before.isAfter(until)))
                .toList();
    }

    /**
     * The message to send when the pane lists other files than those the pane in use, {@code inUse}, follows (ADR
     * 141); {@code branches} is what the branches follow.
     */
    public Option<Msg> followChange(Model model, Option<PaneId> inUse, Option<Followed> branches) {
        return FollowedFiles.change(model, inUse, branches);
    }

    /**
     * The message to send when the highlighted node is not the one whose diff {@code model} holds: of the files of
     * the commit while they are open, else of the working tree.
     */
    public Option<Msg> selectionChange(Model model) {
        return model.repoView()
                .files()
                .commitFiles()
                .map(this::commitSelectionChange)
                .getOrElse(() -> workingSelectionChange(model));
    }

    /**
     * The message to send when the node highlighted in the files of a commit is not the one the content panel shows:
     * its diff, or, among all files of the commit, the file or the directory it did not change (ADR 128).
     */
    private Option<Msg> commitSelectionChange(CommitFilesView view) {
        // While they are read, the files listed before may still be listed in their place: nothing of these is
        // highlighted yet.
        if (view.listedFiles() instanceof Loadable.Loading<List<CommitFile>>) {
            return Option.none();
        }
        Option<FileTree<CommitFile>> selected = commitTreeOf(view).selected();
        if (selected.exists(node -> view.allFiles() && !hasChanges(node, FilesPanel::changed))) {
            return unchangedSelection(selected.get()).filter(msg -> !shows(view.unchanged(), msg));
        }
        Option<List<CommitFile>> entries =
                selected.map(node -> node.directEntries().filter(FilesPanel::changed));
        // Coming from a node without changes, its diff shows again even when it is the one read last.
        return view.unchanged().isDefined()
                ? entries.map(SelectionMsg.CommitFilesSelected::new)
                : PaneKeys.selectionChange(entries, view.diff(), SelectionMsg.CommitFilesSelected::new);
    }

    /**
     * What highlighting {@code node}, without changes, asks to show: a file's content, or how many files a directory
     * holds.
     */
    private <T extends Pathed> Option<Msg> unchangedSelection(FileTree<T> node) {
        return Option.of(
                node instanceof FileTree.File<T> file
                        ? new SelectionMsg.UnchangedFileSelected(file.path())
                        : new SelectionMsg.UnchangedDirectorySelected(node.path(), count(node)));
    }

    /**
     * Whether the commit whose files are listed changed {@code file}: among all its files, most it did not.
     */
    static boolean changed(CommitFile file) {
        return file.type() != ChangeType.UNCHANGED;
    }

    /**
     * The tree of the files of a commit shown: of all files of the commit, or of those it changed (ADR 128).
     */
    private FileTreeList<CommitFile> commitTreeOf(CommitFilesView view) {
        return view.allFiles() ? commitAllTree : commitTree;
    }

    /**
     * The message to send when the node highlighted in the tree of the working tree is not the one the content panel
     * shows: its diff, or, among all files, the file or the directory without changes (ADR 108).
     */
    private Option<Msg> workingSelectionChange(Model model) {
        RepoView view = model.repoView();
        Option<FileTree<FileEntry>> selected = workingTree(view).selected();
        if (selected.exists(node -> view.files().shown().all() && !hasChanges(node))) {
            return unchangedSelection(selected.get())
                    .filter(msg -> !shows(view.files().unchanged(), msg));
        }
        Option<List<FileEntry>> entries =
                selected.map(node -> node.directEntries().filter(FileEntry::changed));
        // Coming from a node without changes, its diff shows again even when it is the one read last.
        return view.files().unchanged().isDefined()
                ? entries.map(SelectionMsg.FilesSelected::new)
                : PaneKeys.selectionChange(entries, view.preview().fileDiff(), SelectionMsg.FilesSelected::new);
    }

    /**
     * Whether {@code shown} is what {@code msg} asks to show.
     */
    private static boolean shows(Option<UnchangedNode> shown, Msg msg) {
        return shown.exists(node -> switch (node) {
            case UnchangedNode.File(var content) ->
                msg instanceof SelectionMsg.UnchangedFileSelected(String path)
                        && content.key().equals(path);
            case UnchangedNode.Directory(String path, int files) ->
                msg.equals(new SelectionMsg.UnchangedDirectorySelected(path, files));
        });
    }

    /**
     * Whether a file under {@code node}, or it itself, has changes; it stops at the first one.
     */
    static boolean hasChanges(FileTree<FileEntry> node) {
        return hasChanges(node, FileEntry::changed);
    }

    /**
     * Whether a file under {@code node}, or it itself, is {@code changed}; it stops at the first one.
     */
    static <T extends Pathed> boolean hasChanges(FileTree<T> node, java.util.function.Predicate<T> changed) {
        return switch (node) {
            case FileTree.File<T> file -> changed.test(file.entry());
            case FileTree.Dir<T> dir -> dir.children().exists(child -> hasChanges(child, changed));
        };
    }

    /**
     * How many files are under {@code node}, counted once for each node.
     */
    private int count(FileTree<?> node) {
        Counted known = counted.filter(last -> last.node() == node)
                .getOrElse(() -> new Counted(node, node.entries().size()));
        counted = Option.of(known);
        return known.files();
    }

    /**
     * The paths of the directories of {@code files} with no changes under them, which a tree of all files of the
     * working tree starts collapsed: it opens on its changes (ADR 108).
     */
    static io.vavr.collection.Set<String> unchangedDirectories(List<FileEntry> files) {
        return unchangedDirectories(files, FileEntry::changed);
    }

    /**
     * The paths of the directories of {@code files} with none {@code changed} under them, which a tree of all files
     * starts collapsed: of the working tree (ADR 108), or of a commit (ADR 128).
     */
    static <T extends Pathed> io.vavr.collection.Set<String> unchangedDirectories(
            List<T> files, java.util.function.Predicate<T> changed) {
        return directories(FileTree.of(files))
                .filter(dir -> !hasChanges(dir, changed))
                .map(FileTree.Dir::path)
                .toSet();
    }

    private static <T extends Pathed> List<FileTree.Dir<T>> directories(List<FileTree<T>> nodes) {
        return nodes.flatMap(node -> node instanceof FileTree.Dir<T> dir
                ? directories(dir.children()).prepend(dir)
                : List.empty());
    }

    /**
     * Moves the highlight of the tree shown in {@code model} to its file {@code step} of the highlighted node, as
     * {@link FileTreeList#step} does, and says whether there was one (ADR 117). The pane does not get the focus.
     */
    public boolean step(Model model, FileStepMsg.Step step) {
        return model.repoView()
                .files()
                .commitFiles()
                .map(view -> commitTreeOf(view).step(step))
                .getOrElse(() -> workingTree(model.repoView()).step(step));
    }

    /**
     * The tree of the working tree shown: of all its files, or of its changed ones (ADR 108).
     */
    private FileTreeList<FileEntry> workingTree(RepoView view) {
        return view.files().shown().all() ? allTree : statusTree;
    }

    /**
     * The files of the working tree listed: all of them merged with its changed ones only when either changed, the
     * last merge otherwise.
     */
    private Loadable<List<FileEntry>> workingFiles(Repo repo, boolean all) {
        if (!all) {
            return repo.workingFiles(false);
        }
        Merged now = merged.filter(last -> last.status() == repo.status() && last.paths() == repo.allFiles())
                .getOrElse(() -> new Merged(repo.status(), repo.allFiles(), repo.workingFiles(true)));
        merged = Option.of(now);
        return now.files();
    }

    /**
     * The pane while {@code pane} has the focus, and is in use.
     */
    public Panel render(Model model, PaneId pane) {
        return render(model, Option.of(pane), Option.of(pane));
    }

    /**
     * @param focus what has the focus, if anything
     * @param inUse the left pane in use, whose highlighted node shows, as it is again when it
     *              is back in use
     */
    public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
        focused = focus.contains(PANE);
        held = false;
        boolean active = inUse.contains(PANE);
        threads = ThreadsByPath.of(model.repoView()
                .files()
                .commitFiles()
                .map(CommitFilesView::threads)
                .getOrElse(List.empty()));
        filter = FileFilterUpdate.shown(model);
        Option<String> shown = FileFilter.shown(filter);
        return model.repoView()
                .files()
                .commitFiles()
                // The files listed before stay listed a moment while the next are read, while the list has the focus
                // (ADR 141): in the pane, it lists those of the one highlighted.
                .map(open -> active ? open : open.shown(model.now()))
                .peek(view -> held = view.held().isDefined())
                // The files of a commit opened from a line blamed on it start on the file of the line (ADR 147).
                .peek(view -> commitTreeOf(view).wanting(view.of(), view.wanted()))
                .map(view -> Pane.bordered(
                        view.ended()
                                        .map(state -> Messages.get(
                                                state == PullRequestDetails.State.MERGED
                                                        ? "panel.commitFiles.titleForMerged"
                                                        : "panel.commitFiles.titleForClosed",
                                                changesName(model, view.of())))
                                        .getOrElse(() -> Messages.get(
                                                "panel.commitFiles.titleFor", changesName(model, view.of())))
                                + listedTitle(view.listed()).map(Messages::get).getOrElse("")
                                + (view.allFiles() ? Messages.get("panel.files.all") : "")
                                + filteredTitle()
                                + lockedTitle(model),
                        withLine(view.held()
                                .map(count -> commitTree.render(
                                        new Held(view.of()),
                                        new Loadable.Loaded<>(List.<CommitFile>empty()),
                                        false,
                                        new FileTreeList.Placeholder(
                                                Placeholders.loading(),
                                                Option.of(Messages.get("panel.commitFiles.held", count))),
                                        Option.none()))
                                .getOrElse(() -> commitTreeOf(view)
                                        .render(view.of(), view.listedFiles(), active, placeholder(view), shown))),
                        focused,
                        commitTreeOf(view).selectedRow(),
                        view.held().isDefined()
                                ? Option.none()
                                : commitTreeOf(view).count(view.listedFiles())))
                .orElse(() -> model.repoView().files().unlisted().map(none -> unlisted(none.why(), lockedTitle(model))))
                .getOrElse(() -> {
                    boolean all = model.repoView().files().shown().all();
                    Loadable<List<FileEntry>> files = workingFiles(model.repo(), all);
                    FileTreeList<FileEntry> tree = workingTree(model.repoView());
                    return Pane.bordered(
                            Messages.get("panel.files.title")
                                    + (all ? Messages.get("panel.files.all") : "")
                                    + filteredTitle()
                                    + lockedTitle(model),
                            withLine(tree.render(ID, files, active, FileTreeList.Placeholder.DEFAULT, shown)),
                            focused,
                            tree.selectedRow(),
                            tree.count(files));
                });
    }

    /**
     * What the title of the pane ends in while it is locked to a list: which list (ADR 149).
     */
    private static String lockedTitle(Model model) {
        return model.repoView()
                .files()
                .locked()
                .map(lock -> Messages.get("panel.files.lockedTo", Messages.get(listTitle(lock))))
                .getOrElse("");
    }

    /**
     * The message key of the title of the list {@code lock} is to.
     */
    private static String listTitle(FilesLock lock) {
        if (lock.pane().equals(Scopes.COMMITS.pane())) {
            return "panel.commits.title";
        }
        if (lock.pane().equals(Scopes.STASH.pane())) {
            return "panel.stash.title";
        }
        return switch (lock.list()) {
            case LIST, OTHER_TAB -> "panel.branches.title";
            case DIVERGENCE -> "panel.divergence.title";
            case BRANCH_COMMITS -> "panel.branchCommits.title";
        };
    }

    /**
     * The pane while the row highlighted in the pane with the focus has no files to list, saying why (ADR 141), its
     * title ending in {@code locked}, which says the list the pane is locked to.
     */
    private Panel unlisted(Unlisted why, String locked) {
        Loadable<List<CommitFile>> none = new Loadable.Loaded<>(List.empty());
        return Pane.bordered(
                switch (why) {
                            case Unlisted.NoBase(var branch) ->
                                Messages.get("panel.commitFiles.titleFor", branch.value());
                            case Unlisted.Stack() -> Messages.get("panel.files.title");
                            case Unlisted.NotHere() -> Messages.get("panel.files.title");
                        }
                        + locked,
                withLine(commitTree.render(
                        why,
                        none,
                        false,
                        new FileTreeList.Placeholder(Placeholders.loading(), Option.of(unlistedText(why))),
                        Option.none())),
                focused,
                commitTree.selectedRow(),
                commitTree.count(none));
    }

    private static String unlistedText(Unlisted why) {
        return switch (why) {
            case Unlisted.NoBase(var branch) -> Messages.get("panel.files.unlisted.noBase", branch.value());
            case Unlisted.Stack() -> Messages.get("panel.files.unlisted.stack");
            case Unlisted.NotHere() -> Messages.get("panel.files.unlisted.notHere");
        };
    }

    /**
     * What the title says after what it says already while the files are filtered, once the filter is typed: the
     * line above the tree shows it meanwhile.
     */
    private String filteredTitle() {
        return filter.filter(typed -> !typed.typing())
                .flatMap(FileFilter::shown)
                .map(text -> Messages.get("panel.files.filtered", text))
                .getOrElse("");
    }

    /**
     * {@code tree}, its scrollbar on the right border of the pane, under the line the filter is typed in while it is
     * open, which keeps the width inside the border.
     */
    private Element withLine(Element tree) {
        Element bordered = BorderBar.of(tree);
        return filter.filter(FileFilter::typing)
                .<Element>map(
                        typing -> new PasteTarget(Toolkit.column(filterLine.field(typing.text()), bordered), ID, paste))
                .getOrElse(bordered);
    }

    /**
     * Whether the files pane, showing {@code files}, shows none of them for its filter: the content panel then says
     * so too, in place of the diff of a node no longer highlighted (ADR 105).
     */
    public static <T extends Pathed> boolean noneMatch(Model model, List<T> files) {
        Option<String> shown = FileFilter.shown(FileFilterUpdate.shown(model));
        return shown.isDefined()
                && !files.isEmpty()
                && FileTreeList.matching(files, shown).isEmpty();
    }

    /**
     * The tree widget of the files of the working tree, which holds their selection and expansion state.
     */
    TreeElement<FileTree<FileEntry>> tree() {
        return statusTree.tree();
    }

    /**
     * The tree widget of all files of the working tree, which holds their selection and expansion state (ADR 108).
     */
    TreeElement<FileTree<FileEntry>> allTree() {
        return allTree.tree();
    }

    /**
     * The tree widget of the files of a commit, which holds their selection and expansion state.
     */
    TreeElement<FileTree<CommitFile>> commitTree() {
        return commitTree.tree();
    }

    /**
     * The tree widget of all files of a commit, which holds their selection and expansion state (ADR 128).
     */
    TreeElement<FileTree<CommitFile>> commitAllTree() {
        return commitAllTree.tree();
    }

    /**
     * How the commit, the stash, the pull request or the branch {@code of} is named: by its short sha, by its
     * {@code stash@{n}}, by its {@code #n}, by its name and the base it is compared with (ADR 61), or the main branch
     * by its name and the commit it is at (ADR 128): what it shows is that commit, not what the branch changed.
     */
    public static String changesName(Model model, Changes of) {
        return switch (of) {
            case Changes.OfCommit(Sha sha) -> sha.shortSha();
            case Changes.OfTip(var branch, Sha sha) ->
                Messages.get("panel.commitFiles.tip", branch.value(), sha.shortSha());
            case Changes.OfStash(Sha sha) ->
                model.repo().stash(sha).map(Stash::ref).getOrElse(sha::shortSha);
            case Changes.OfPullRequest(var commits) -> "#" + commits.number();
            case Changes.OfBranch(var branch, var base, var sha) ->
                Messages.get(
                        "panel.commitFiles.branch", branch.value(), base.ref().name());
        };
    }

    /**
     * All files of the working tree, merged from {@code status} and {@code paths}.
     */
    private record Merged(Loadable<RepoStatus> status, Loadable<List<String>> paths, Loadable<List<FileEntry>> files) {}

    /**
     * How many files are under {@code node}.
     */
    private record Counted(FileTree<?> node, int files) {}

    /**
     * What the tree of files that follow the highlight is built for while they are counted in its place, rather than
     * listed: the tree is built anew once they are.
     */
    private record Held(Changes of) {}
}
