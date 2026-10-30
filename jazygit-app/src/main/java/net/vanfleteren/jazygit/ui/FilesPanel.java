package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
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
    // The unresolved review threads of the pull request whose files are listed, as last rendered (ADR 55).
    private List<ReviewThread> unresolved = List.empty();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     * @param openDiff called when Enter is pressed on a file or directory, to move focus to its diff
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
                event -> event.code() == KeyCode.ENTER && statusTree.selected().isDefined()
                        ? Option.of(openDiff)
                        : PaneKeys.sending(keymap.handle(Scopes.FILES, event, statusTree.selected()), dispatch)));
        commitTree.onKey(PaneKeys.whileFocused(
                () -> focused,
                event -> event.code() == KeyCode.ENTER && commitTree.selected().isDefined()
                        ? Option.of(openDiff)
                        : PaneKeys.sending(
                                keymap.handle(Scopes.COMMIT_FILES, event, commitTree.selected()), dispatch)));
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
     * request after that how many unresolved review threads they have, if any (ADR 55).
     */
    private StyledElement<?> drawCommitFile(FileTree.File<CommitFile> file) {
        return Toolkit.row(List.<StyledElement<?>>of(
                        Toolkit.text(file.entry().type().marker()).addClass(Theme.Classes.STAGED),
                        Toolkit.text(" " + name(file)))
                .appendAll(Option.of(unresolved.count(thread ->
                                thread.isOn(file.entry().path(), file.entry().origin())))
                        .filter(count -> count > 0)
                        .map(count -> Toolkit.text(" 💬" + count).addClass(Theme.Classes.DIFF_THREAD)))
                .toJavaArray(StyledElement[]::new));
    }

    /**
     * The review threads of {@code view} that are not resolved.
     */
    private static List<ReviewThread> unresolved(CommitFilesView view) {
        return view.threads().filter(thread -> !thread.resolved());
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
        unresolved = model.repoView().commitFiles().map(FilesPanel::unresolved).getOrElse(List.empty());
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
                                        "panel.commitFiles.titleFor", ContentView.changesName(model, view.of()))),
                        commitTree.render(view.of(), view.files(), active),
                        focused,
                        commitTree.selectedRow(),
                        commitTree.count(view.files())))
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
