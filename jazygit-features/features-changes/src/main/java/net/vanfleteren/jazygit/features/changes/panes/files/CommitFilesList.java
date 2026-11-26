package net.vanfleteren.jazygit.features.changes.panes.files;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.FileTree;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.diff.RemarksByPath;
import net.vanfleteren.jazygit.kernel.state.diff.StatedRemarks;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.Unlisted;
import net.vanfleteren.jazygit.kernel.ui.files.ListedFiles;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The files a commit, a stash, a pull request or a branch changed, in the files pane while they are open (ADR 44); or,
 * after {@code a}, all files of that commit (ADR 128), each in a tree of its own. The tree opens on the first directory
 * that holds files directly, so its diff shows at once; the tree of all files only on the directories with changes.
 * The files of a pull request say how many review threads they have (ADR 55, 57).
 */
final class CommitFilesList {

    private final FileTreeList<CommitFile> commitTree;
    // All files of the commit whose files are listed, kept apart from those it changed, as for the working tree
    // (ADR 128).
    private final FileTreeList<CommitFile> commitAllTree;
    private final FileNodes nodes = new FileNodes();
    // Whether the files listed are counted in place of their tree, as there are many (ADR 141).
    private boolean held;
    // The remark threads stated for the files listed, by their path, as last rendered (ADR 55, 171).
    private RemarksByPath threads = RemarksByPath.of(List.empty());

    CommitFilesList(Theme.Highlights highlights, TreeKeys keys) {
        commitTree = new FileTreeList<>(
                FilesPanel.ID,
                file -> file.entry().type().marker() + " " + FileNodes.name(file),
                this::drawCommitFile,
                highlights,
                FileTreeList::firstWithDiff);
        commitAllTree = new FileTreeList<>(
                FilesPanel.ID,
                file -> file.entry().type().marker() + " " + FileNodes.name(file),
                this::drawCommitFile,
                highlights,
                FileTreeList::firstWithDiff,
                files -> FileNodes.unchangedDirectories(files, CommitFilesList::changed));
        List.of(commitTree, commitAllTree)
                .forEach(tree -> keys.bind(
                        tree,
                        // Enter lists the files counted in place of their tree (ADR 141).
                        event -> (held && event.isConfirm()
                                        ? PaneKeys.sending(Option.of(new SelectionMsg.WholeListed()), keys.dispatch())
                                        : Option.<Runnable>none())
                                .orElse(() -> keys.diffOrKeyed(Scopes.COMMIT_FILES, event, tree))));
    }

    /**
     * The pane, listing the files of {@code open} in {@code frame}.
     */
    Panel render(Model model, CommitFilesView open, ListFrame frame) {
        threads = RemarksByPath.of(model.remarks().map(StatedRemarks::read).getOrElse(List.empty()));
        // The files listed before stay listed a moment while the next are read, while the list has the focus
        // (ADR 141): in the pane, it lists those of the one highlighted.
        CommitFilesView view = frame.active() ? open : open.shown(model.now());
        held = view.held().isDefined();
        FileTreeList<CommitFile> tree = tree(view);
        // The files of a commit opened from a line blamed on it start on the file of the line (ADR 147).
        tree.wanting(view.of(), view.wanted());
        nodes.switching(view.of(), tree, view.allFiles() ? commitTree : commitAllTree);
        return frame.bordered(
                title(model, view.of())
                        + listedTitle(view.listed()).map(Messages::get).getOrElse("")
                        + (view.allFiles() ? Messages.get("changes.panes.files.all") : "")
                        + frame.filtered()
                        + frame.locked(),
                view.held()
                        .map(count -> commitTree.render(
                                new Held(view.of()),
                                new Loadable.Loaded<>(List.<CommitFile>empty()),
                                false,
                                new FileTreeList.Placeholder(
                                        Placeholders.loading(),
                                        Option.of(Messages.get("changes.panes.files.commit.held", count))),
                                Option.none()))
                        .getOrElse(() -> tree.render(
                                view.of(), view.listedFiles(model), frame.active(), placeholder(view), frame.shown())),
                tree.selectedRow(),
                view.held().isDefined() ? Option.none() : tree.count(view.listedFiles(model)));
    }

    /**
     * The title of the files of {@code of}: of a pull request, whether it is merged or closed.
     */
    private static String title(Model model, Changes of) {
        return model.remarks(of)
                .flatMap(StatedRemarks::ended)
                .map(ended -> Messages.get(
                        ended == StatedRemarks.Ended.MERGED
                                ? "changes.panes.files.commit.titleForMerged"
                                : "changes.panes.files.commit.titleForClosed",
                        ListedFiles.changesName(model, of)))
                .getOrElse(() -> Messages.get("panel.commitFiles.titleFor", ListedFiles.changesName(model, of)));
    }

    /**
     * The pane while the row highlighted in the pane with the focus has no files to list, saying why (ADR 141), its
     * title ending in the list the pane is locked to.
     */
    Panel unlisted(Unlisted why, ListFrame frame) {
        // Nothing is counted here, whatever the files listed before were: Enter goes to the keymap.
        held = false;
        Loadable<List<CommitFile>> none = new Loadable.Loaded<>(List.empty());
        return frame.bordered(
                switch (why) {
                            case Unlisted.NoBase(var branch) ->
                                Messages.get("panel.commitFiles.titleFor", branch.value());
                            case Unlisted.Group() -> Messages.get("panel.files.title");
                            case Unlisted.NotHere() -> Messages.get("panel.files.title");
                        }
                        + frame.locked(),
                commitTree.render(
                        why,
                        none,
                        false,
                        new FileTreeList.Placeholder(Placeholders.loading(), Option.of(unlistedText(why))),
                        Option.none()),
                commitTree.selectedRow(),
                commitTree.count(none));
    }

    private static String unlistedText(Unlisted why) {
        return switch (why) {
            case Unlisted.NoBase(var branch) -> Messages.get("changes.panes.files.unlisted.noBase", branch.value());
            case Unlisted.Group() -> Messages.get("changes.panes.files.unlisted.group");
            case Unlisted.NotHere() -> Messages.get("changes.panes.files.unlisted.notHere");
        };
    }

    /**
     * The message to send when the node highlighted is not the one the content panel shows: its diff, or, among all
     * files of the commit, the file or the directory it did not change (ADR 128).
     */
    Option<Msg> selectionChange(Model model, CommitFilesView view) {
        // While they are read, the files listed before may still be listed in their place: nothing of these is
        // highlighted yet.
        if (view.listedFiles(model) instanceof Loadable.Loading<List<CommitFile>>) {
            return Option.none();
        }
        Option<FileTree<CommitFile>> selected = tree(view).selected();
        if (selected.exists(node -> view.allFiles() && !FileNodes.hasChanges(node, CommitFilesList::changed))) {
            return nodes.unchangedSelection(selected.get()).filter(msg -> !FileNodes.shows(view.unchanged(), msg));
        }
        Option<List<CommitFile>> entries =
                selected.map(node -> node.directEntries().filter(CommitFilesList::changed));
        // Coming from a node without changes, its diff shows again even when it is the one read last.
        return view.unchanged().isDefined()
                ? entries.map(SelectionMsg.CommitFilesSelected::new)
                : PaneKeys.selectionChange(entries, view.diff(), SelectionMsg.CommitFilesSelected::new);
    }

    /**
     * Moves the highlight of the tree of {@code view} to its file {@code step} of the highlighted node, and says
     * whether there was one (ADR 117).
     */
    boolean step(CommitFilesView view, FileStepMsg.Step step) {
        return tree(view).step(step);
    }

    /**
     * The tree of the files of {@code view}: of all files of the commit, or of those it changed (ADR 128).
     */
    private FileTreeList<CommitFile> tree(CommitFilesView view) {
        return view.allFiles() ? commitAllTree : commitTree;
    }

    /**
     * Whether the commit whose files are listed changed {@code file}: among all its files, most it did not.
     */
    static boolean changed(CommitFile file) {
        return file.type() != ChangeType.UNCHANGED;
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
        List<RemarkThread> on = threads.on(file.entry().path(), file.entry().origin());
        int resolved = on.count(RemarkThread::resolved);
        return Toolkit.row(List.<StyledElement<?>>of(
                        Toolkit.text(file.entry().type().marker()).addClass(Theme.Classes.STAGED),
                        Toolkit.text(" " + FileNodes.name(file)))
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
            case UNRESOLVED -> Option.of("changes.panes.files.commit.unresolved");
            case REMARKED -> Option.of("changes.panes.files.commit.remarked");
        };
    }

    /**
     * What the tree of {@code view} says while its files are read, and when none is listed: once they are read, only
     * some are listed while the review threads are read, and it says why none is when only some are (ADR 57).
     */
    private static FileTreeList.Placeholder placeholder(CommitFilesView view) {
        return new FileTreeList.Placeholder(
                view.shownFiles() instanceof Loadable.Loaded<?>
                        ? Messages.get("changes.panes.files.commit.readingRemarks")
                        : Placeholders.loading(),
                ListedFiles.nothingListed(view.listed()));
    }

    /**
     * The tree widget of the files changed, which holds their selection and expansion state.
     */
    TreeElement<FileTree<CommitFile>> commitTree() {
        return commitTree.tree();
    }

    /**
     * The tree widget of all files of the commit, which holds their selection and expansion state (ADR 128).
     */
    TreeElement<FileTree<CommitFile>> commitAllTree() {
        return commitAllTree.tree();
    }

    /**
     * What the tree of files that follow the highlight is built for while they are counted in its place, rather than
     * listed: the tree is built anew once they are.
     */
    private record Held(Changes of) {}
}
