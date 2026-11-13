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
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.theme.Theme;
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
 * only on the directories with changes.
 */
public class FilesPanel {

    public static final String ID = Scopes.FILES.id();

    private final FileTreeList<FileEntry> statusTree;
    // All files of the working tree, kept apart from its changed files, so that each keeps its highlight and the
    // directories collapsed in it as the files pane switches between them (ADR 108).
    private final FileTreeList<FileEntry> allTree;
    private final FileTreeList<CommitFile> commitTree;
    private final Keymap keymap = Keymap.standard();
    private final FilterLine filterLine = new FilterLine();
    // What a paste does: while the filter line is open, it goes in it.
    private final Consumer<PasteEvent> paste;
    // The trees' key handlers also see keys typed in other panes, so they must know whether the pane has focus.
    private boolean focused;
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
        List.of(statusTree, allTree)
                .forEach(tree -> tree.onKey(PaneKeys.whileFocused(
                        () -> focused,
                        event -> filtered(event, dispatch)
                                .orElse(() -> opensDiff(event, tree)
                                        ? Option.of(openDiff)
                                        : PaneKeys.sending(
                                                keymap.handle(Scopes.FILES, event, tree.selected()), dispatch)))));
        commitTree.onKey(PaneKeys.whileFocused(
                () -> focused,
                event -> filtered(event, dispatch)
                        .orElse(() -> opensDiff(event, commitTree)
                                ? Option.of(openDiff)
                                : PaneKeys.sending(
                                        keymap.handle(Scopes.COMMIT_FILES, event, commitTree.selected()), dispatch))));
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
                view.files() instanceof Loadable.Loaded<?>
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
     * The message to send when the highlighted node is not the one whose diff {@code model} holds: of the files of
     * the commit while they are open, else of the working tree.
     */
    public Option<Msg> selectionChange(Model model) {
        return model.repoView()
                .commitFiles()
                .map(view -> PaneKeys.selectionChange(
                        commitTree.selected().map(FileTree::directEntries),
                        view.diff(),
                        SelectionMsg.CommitFilesSelected::new))
                .getOrElse(() -> workingSelectionChange(model));
    }

    /**
     * The message to send when the node highlighted in the tree of the working tree is not the one the content panel
     * shows: its diff, or, among all files, the file or the directory without changes (ADR 108).
     */
    private Option<Msg> workingSelectionChange(Model model) {
        RepoView view = model.repoView();
        Option<FileTree<FileEntry>> selected = workingTree(view).selected();
        if (selected.exists(node -> view.allFiles() && !hasChanges(node))) {
            FileTree<FileEntry> node = selected.get();
            return Option.<Msg>of(
                            node instanceof FileTree.File<FileEntry> file
                                    ? new SelectionMsg.UnchangedFileSelected(file.path())
                                    : new SelectionMsg.UnchangedDirectorySelected(node.path(), count(node)))
                    .filter(msg -> !shows(view.unchanged(), msg));
        }
        Option<List<FileEntry>> entries =
                selected.map(node -> node.directEntries().filter(FileEntry::changed));
        // Coming from a node without changes, its diff shows again even when it is the one read last.
        return view.unchanged().isDefined()
                ? entries.map(SelectionMsg.FilesSelected::new)
                : PaneKeys.selectionChange(entries, view.fileDiff(), SelectionMsg.FilesSelected::new);
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
        return switch (node) {
            case FileTree.File<FileEntry> file -> file.entry().changed();
            case FileTree.Dir<FileEntry> dir -> dir.children().exists(FilesPanel::hasChanges);
        };
    }

    /**
     * How many files are under {@code node}, counted once for each node.
     */
    private int count(FileTree<FileEntry> node) {
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
        return directories(FileTree.of(files))
                .filter(dir -> !hasChanges(dir))
                .map(FileTree.Dir::path)
                .toSet();
    }

    private static List<FileTree.Dir<FileEntry>> directories(List<FileTree<FileEntry>> nodes) {
        return nodes.flatMap(node -> node instanceof FileTree.Dir<FileEntry> dir
                ? directories(dir.children()).prepend(dir)
                : List.empty());
    }

    /**
     * Moves the highlight of the tree shown in {@code model} to its file {@code step} of the highlighted node, as
     * {@link FileTreeList#step} does, and says whether there was one (ADR 117). The pane does not get the focus.
     */
    public boolean step(Model model, FileStepMsg.Step step) {
        return model.repoView().commitFiles().isDefined()
                ? commitTree.step(step)
                : workingTree(model.repoView()).step(step);
    }

    /**
     * The tree of the working tree shown: of all its files, or of its changed ones (ADR 108).
     */
    private FileTreeList<FileEntry> workingTree(RepoView view) {
        return view.allFiles() ? allTree : statusTree;
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
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return render(model, focusedId, focusedId);
    }

    /**
     * @param activeId the id of the left pane in use, whose highlighted node shows, as it is again when it
     *                 is back in use
     */
    public Panel render(Model model, String focusedId, String activeId) {
        focused = ID.equals(focusedId);
        boolean active = ID.equals(activeId);
        threads = ThreadsByPath.of(
                model.repoView().commitFiles().map(CommitFilesView::threads).getOrElse(List.empty()));
        filter = FileFilterUpdate.shown(model);
        Option<String> shown = FileFilter.shown(filter);
        return model.repoView()
                .commitFiles()
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
                                + filteredTitle(),
                        withLine(commitTree.render(view.of(), view.listedFiles(), active, placeholder(view), shown)),
                        focused,
                        commitTree.selectedRow(),
                        commitTree.count(view.listedFiles())))
                .getOrElse(() -> {
                    boolean all = model.repoView().allFiles();
                    Loadable<List<FileEntry>> files = workingFiles(model.repo(), all);
                    FileTreeList<FileEntry> tree = workingTree(model.repoView());
                    return Pane.bordered(
                            Messages.get("panel.files.title")
                                    + (all ? Messages.get("panel.files.all") : "")
                                    + filteredTitle(),
                            withLine(tree.render(ID, files, active, FileTreeList.Placeholder.DEFAULT, shown)),
                            focused,
                            tree.selectedRow(),
                            tree.count(files));
                });
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
     * {@code tree}, under the line the filter is typed in while it is open.
     */
    private Element withLine(Element tree) {
        return filter.filter(FileFilter::typing)
                .<Element>map(
                        typing -> new PasteTarget(Toolkit.column(filterLine.field(typing.text()), tree), ID, paste))
                .getOrElse(tree);
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
     * How the commit, the stash, the pull request or the branch {@code of} is named: by its short sha, by its
     * {@code stash@{n}}, by its {@code #n}, or by its name and the base it is compared with (ADR 61).
     */
    public static String changesName(Model model, Changes of) {
        return switch (of) {
            case Changes.OfCommit(Sha sha) -> sha.shortSha();
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
    private record Counted(FileTree<FileEntry> node, int files) {}
}
