package net.vanfleteren.jazygit.features.changes.panes.files;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.FileTree;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Repo;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The files of the working tree in the files pane: its changed files, as in {@code git status}, or, after {@code a},
 * all its files (ADR 108), each in a tree of its own that keeps its highlight and the directories collapsed in it. The
 * tree of all files opens only on the directories with changes. What the features mark a file with comes after its
 * name, and what they add to the title after what it is (ADR 172).
 */
final class WorkingTreeFilesList {

    private final FileTreeList<FileEntry> statusTree;
    // All files of the working tree, kept apart from its changed files, so that each keeps its highlight and the
    // directories collapsed in it as the files pane switches between them (ADR 108).
    private final FileTreeList<FileEntry> allTree;
    // What the features give for the slots of the pane (ADR 172).
    private final Fills fills;
    private final FileNodes nodes = new FileNodes();
    // What the features mark the files with, as last rendered (ADR 172).
    private List<FileMark> marks = List.empty();
    // All files of the working tree, as last merged, with the status and the paths they were merged from: merging
    // them again on every frame would cost tens of milliseconds in a repository of 100 000 files (ADR 108).
    private Option<Merged> merged = Option.none();

    /**
     * @param locked whether the pane is locked to a list, which Escape lets go of once the filter is cleared (ADR 149)
     */
    WorkingTreeFilesList(Theme.Highlights highlights, Fills fills, TreeKeys keys, BooleanSupplier locked) {
        this.fills = fills;
        statusTree = new FileTreeList<>(
                FilesPanel.ID,
                file -> file.entry().type().marker() + " " + FileNodes.name(file),
                this::drawEntry,
                highlights,
                FileTreeList::first);
        allTree = new FileTreeList<>(
                FilesPanel.ID,
                file -> file.entry().type().marker() + " " + FileNodes.name(file),
                this::drawEntry,
                highlights,
                FileTreeList::first,
                WorkingTreeFilesList::unchangedDirectories);
        List.of(statusTree, allTree)
                .forEach(tree -> keys.bind(
                        tree,
                        // Locked, the working tree is what the list follows: Escape lets go there as in the files of
                        // a commit, once the filter is cleared (ADR 149).
                        event -> (locked.getAsBoolean() && event.isCancel()
                                        ? PaneKeys.sending(Option.of(new SelectionMsg.FilesUnlocked()), keys.dispatch())
                                        : Option.<Runnable>none())
                                // A feature that binds Enter for what is highlighted has it before the diff, as the
                                // resolving of a file in conflict does (ADR 200); Right still opens the diff.
                                .orElse(() -> event.isConfirm() ? keys.keyed(Scopes.FILES, event, tree) : Option.none())
                                .orElse(() -> keys.diffOrKeyed(Scopes.FILES, event, tree))));
    }

    /**
     * The pane, listing the files of the working tree of {@code model} in {@code frame}.
     */
    Panel render(Model model, ListFrame frame) {
        boolean all = model.repoView().files().shown().all();
        Loadable<List<FileEntry>> files = files(model.repo(), all);
        FileTreeList<FileEntry> tree = tree(model.repoView());
        nodes.switching(FilesPanel.ID, tree, all ? statusTree : allTree);
        marks = fills.all(FilesPane.MARK, model);
        return frame.bordered(
                Messages.get("panel.files.title")
                        + fills.all(FilesPane.TITLE, model)
                                .map(part -> Messages.get("changes.panes.files.titlePart", ErrorText.of(part)))
                                .mkString()
                        + (all ? Messages.get("changes.panes.files.all") : "")
                        + frame.filtered()
                        + frame.locked(),
                tree.render(FilesPanel.ID, files, frame.active(), FileTreeList.Placeholder.DEFAULT, frame.shown()),
                tree.selectedRow(),
                tree.count(files));
    }

    /**
     * The message to send when the node highlighted is not the one the content panel shows: its diff, or the file or
     * the directory without changes, among all files (ADR 108) or resolved (ADR 198).
     */
    Option<Msg> selectionChange(Model model) {
        RepoView view = model.repoView();
        Option<FileTree<FileEntry>> selected = tree(view).selected();
        // Among the changed files, a node without changes is a file resolved to what HEAD has (ADR 198).
        if (selected.exists(node -> !hasChanges(node))) {
            return nodes.unchangedSelection(selected.get())
                    .filter(msg -> !FileNodes.shows(view.files().unchanged(), msg));
        }
        Option<List<FileEntry>> entries =
                selected.map(node -> node.directEntries().filter(FileEntry::changed));
        // Coming from a node without changes, its diff shows again even when it is the one read last.
        return view.files().unchanged().isDefined()
                ? entries.map(SelectionMsg.FilesSelected::new)
                : PaneKeys.selectionChange(entries, view.preview().fileDiff(), SelectionMsg.FilesSelected::new);
    }

    /**
     * Moves the highlight of the tree shown to its file {@code step} of the highlighted node, and says whether there
     * was one (ADR 117).
     */
    boolean step(RepoView view, FileStepMsg.Step step) {
        return tree(view).step(step);
    }

    /**
     * The tree shown: of all files, or of the changed ones (ADR 108).
     */
    private FileTreeList<FileEntry> tree(RepoView view) {
        return view.files().shown().all() ? allTree : statusTree;
    }

    /**
     * The files listed: all of them merged with the changed ones only when either changed, the last merge otherwise.
     */
    private Loadable<List<FileEntry>> files(Repo repo, boolean all) {
        if (!all) {
            return repo.workingFiles(false);
        }
        Merged now = merged.filter(last -> last.status() == repo.status() && last.paths() == repo.allFiles())
                .getOrElse(() -> new Merged(repo.status(), repo.allFiles(), repo.workingFiles(true)));
        merged = Option.of(now);
        return now.files();
    }

    /**
     * Files show two status columns like {@code git status --short}: the staged change in green, then the unstaged
     * change in red. Untracked files show a red {@code ??}. Files in a merge conflict show a {@code U} for each branch
     * that updated them: green for the current branch, red for the other. What the features mark a file with comes
     * after its name, as a file resolved since the operation stopped (ADR 198); a mark may dim the whole row, as for a
     * file listed only to be found again.
     */
    private StyledElement<?> drawEntry(FileTree.File<FileEntry> file) {
        List<FileMark.Mark> on = marks.flatMap(mark -> mark.of(file.entry()));
        if (on.isEmpty()) {
            return plainEntry(file);
        }
        boolean dims = on.exists(FileMark.Mark::dims);
        return Toolkit.row(on.flatMap(FileMark.Mark::after)
                .<StyledElement<?>>map(seg -> segment(seg, dims))
                .prepend(dims ? plainEntry(file).dim() : plainEntry(file))
                .toJavaArray(StyledElement[]::new));
    }

    /**
     * {@code seg}, in its colour, dimmed if {@code dims}.
     */
    private static StyledElement<?> segment(LoadableList.Seg seg, boolean dims) {
        StyledElement<?> text = seg.cssClass()
                .<StyledElement<?>>map(css -> Toolkit.text(seg.text()).addClass(css))
                .getOrElse(() -> Toolkit.text(seg.text()));
        return dims ? text.dim() : text;
    }

    private static StyledElement<?> plainEntry(FileTree.File<FileEntry> file) {
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
                Toolkit.text(" " + FileNodes.name(file)));
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
     * {@code U} for a branch that updated the conflicting file, {@code D} for one that deleted it.
     */
    private static String marker(Conflict.Side side) {
        return side == Conflict.Side.DELETED ? "D" : "U";
    }

    /**
     * Whether a file under {@code node}, or it itself, has changes; it stops at the first one.
     */
    static boolean hasChanges(FileTree<FileEntry> node) {
        return FileNodes.hasChanges(node, FileEntry::changed);
    }

    /**
     * The paths of the directories of {@code files} with no changes under them, which the tree of all files starts
     * collapsed: it opens on its changes (ADR 108).
     */
    static Set<RepoPath> unchangedDirectories(List<FileEntry> files) {
        return FileNodes.unchangedDirectories(files, FileEntry::changed);
    }

    /**
     * The tree widget of the changed files, which holds their selection and expansion state.
     */
    TreeElement<FileTree<FileEntry>> statusTree() {
        return statusTree.tree();
    }

    /**
     * The tree widget of all files, which holds their selection and expansion state (ADR 108).
     */
    TreeElement<FileTree<FileEntry>> allTree() {
        return allTree.tree();
    }

    /**
     * All files of the working tree, merged from {@code status} and {@code paths}.
     */
    private record Merged(
            Loadable<RepoStatus> status, Loadable<List<RepoPath>> paths, Loadable<List<FileEntry>> files) {}
}
