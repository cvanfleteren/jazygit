package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
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
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * Left-side panel showing the changed files, as in {@code git status}, as a directory tree; or, while they are open,
 * the files a commit or a stash changed (ADR 44). Each tree keeps its own selection and expansion, so the files of the
 * working tree are as they were when those of a commit are closed. The files of a commit open on the first directory
 * that holds files directly, so its diff shows at once.
 */
public class FilesPanel {

    public static final String ID = Scopes.FILES.id();

    private final FileTreeList<FileEntry> statusTree;
    private final FileTreeList<CommitFile> commitTree;
    private final Keymap keymap = Keymap.standard();
    // The trees' key handlers also see keys typed in other panes, so they must know whether the pane has focus.
    private boolean focused;
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
        statusTree = new FileTreeList<>(
                ID,
                file -> file.entry().type().marker() + " " + name(file),
                FilesPanel::drawEntry,
                highlights,
                FileTreeList::first);
        commitTree = new FileTreeList<>(
                ID,
                file -> file.entry().type().marker() + " " + name(file),
                this::drawCommitFile,
                highlights,
                FileTreeList::firstWithDiff);
        statusTree.onKey(PaneKeys.whileFocused(
                () -> focused,
                event -> opensDiff(event, statusTree)
                        ? Option.of(openDiff)
                        : PaneKeys.sending(keymap.handle(Scopes.FILES, event, statusTree.selected()), dispatch)));
        commitTree.onKey(PaneKeys.whileFocused(
                () -> focused,
                event -> opensDiff(event, commitTree)
                        ? Option.of(openDiff)
                        : PaneKeys.sending(
                                keymap.handle(Scopes.COMMIT_FILES, event, commitTree.selected()), dispatch)));
    }

    /**
     * Whether {@code event} moves the focus to the diff of what {@code tree} has highlighted: Enter does, and so does
     * Right, except on a collapsed directory, which it expands as in any tree.
     */
    private static boolean opensDiff(KeyEvent event, FileTreeList<?> tree) {
        return tree.selected().isDefined()
                && (event.code() == KeyCode.ENTER || (event.matches(Actions.MOVE_RIGHT) && !tree.collapsedSelected()));
    }

    /**
     * Files show two status columns like {@code git status --short}: the staged change in green, then
     * the unstaged change in red. Untracked files show a red {@code ??}. Files in a merge conflict show a
     * {@code U} for each branch that updated them: green for the current branch, red for the other.
     */
    private static StyledElement<?> drawEntry(FileTree.File<FileEntry> file) {
        FileEntry entry = file.entry();
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
    static Option<String> nothingListed(CommitFilesView.Listed listed) {
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
                .getOrElse(() -> PaneKeys.selectionChange(
                        statusTree.selected().map(FileTree::directEntries),
                        model.repoView().fileDiff(),
                        SelectionMsg.FilesSelected::new));
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
        return model.repoView()
                .commitFiles()
                .map(view -> Pane.bordered(
                        view.ended()
                                        .map(state -> Messages.get(
                                                state == PullRequestDetails.State.MERGED
                                                        ? "panel.commitFiles.titleForMerged"
                                                        : "panel.commitFiles.titleForClosed",
                                                ContentView.changesName(model, view.of())))
                                        .getOrElse(() -> Messages.get(
                                                "panel.commitFiles.titleFor",
                                                ContentView.changesName(model, view.of())))
                                + listedTitle(view.listed()).map(Messages::get).getOrElse(""),
                        commitTree.render(view.of(), view.listedFiles(), active, placeholder(view)),
                        focused,
                        commitTree.selectedRow(),
                        commitTree.count(view.listedFiles())))
                .getOrElse(() -> {
                    Loadable<List<FileEntry>> files = model.repo().status().map(RepoStatus::files);
                    return Pane.bordered(
                            Messages.get("panel.files.title"),
                            statusTree.render(ID, files, active),
                            focused,
                            statusTree.selectedRow(),
                            statusTree.count(files));
                });
    }

    /**
     * The tree widget of the files of the working tree, which holds their selection and expansion state.
     */
    TreeElement<FileTree<FileEntry>> tree() {
        return statusTree.tree();
    }

    /**
     * The tree widget of the files of a commit, which holds their selection and expansion state.
     */
    TreeElement<FileTree<CommitFile>> commitTree() {
        return commitTree.tree();
    }
}
