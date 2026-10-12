package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.tree.TreeNode;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.Pane;

import java.util.function.Consumer;

/**
 * Left-side panel showing the changed files, as in {@code git status}, as a directory tree. The tree
 * widget keeps its own selection and expansion, so it is only rebuilt when the status changes, and
 * then keeps the collapsed directories and the selected path.
 */
public class FilesPanel {

    public static final String ID = Scopes.FILES.id();

    // A virtual directory holding everything, so that all changes can be (un)staged at once.
    private static final String ROOT_NAME = "/";


    private final TreeElement<FileTree> tree = Toolkit.<FileTree>tree()
            .id(ID)
            .focusable()
            .scrollbar(ScrollBarPolicy.AS_NEEDED)
            .nodeRenderer(FilesPanel::renderNode);
    private List<TreeNode<FileTree>> roots = List.of();
    private Loadable<RepoStatus> shown;
    // The tree's key handler also sees keys typed in other panes, so it must know whether it has focus.
    private boolean focused;
    private final Keymap keymap = Keymap.standard();
    private final Runnable openDiff;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public FilesPanel(Consumer<Msg> dispatch) {
        this(dispatch, () -> { });
    }

    /**
     * @param dispatch  receives the messages for the user's actions, on the render thread
     * @param openDiff  called when Enter is pressed on a file or directory, to move focus to its diff
     */
    public FilesPanel(Consumer<Msg> dispatch, Runnable openDiff) {
        this.openDiff = openDiff;
        tree.onKeyEvent(event -> handleKey(event, dispatch));
    }

    /**
     * The message to send when the highlighted node is not the one whose diff {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return Option.of(tree.selectedNode())
                .flatMap(node -> Option.of(node.data()))
                .map(FileTree::directEntries)
                .filter(files -> !model.fileDiff().map(FileDiff::files).equals(Option.of(files)))
                .map(SelectionMsg.FilesSelected::new);
    }

    private EventResult handleKey(KeyEvent event, Consumer<Msg> dispatch) {
        if (focused && event.code() == KeyCode.ENTER && tree.selectedNode() != null) {
            openDiff.run();
            return EventResult.HANDLED;
        }
        return Option.of(event)
                .filter(e -> focused)
                .flatMap(this::request)
                .map(msg -> {
                    dispatch.accept(msg);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    private Option<Msg> request(KeyEvent event) {
        return keymap.handle(Scopes.FILES, event, Option.of(tree.selectedNode()).flatMap(node -> Option.of(node.data())));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        focused = ID.equals(focusedId);
        if (model.status() != shown) {
            shown = model.status();
            rebuild(shown);
        }
        // Counted on every render: expanding or collapsing a directory changes the visible rows
        // without a rebuild.
        Option<Integer> count = shown instanceof Loadable.Loaded<RepoStatus>
                ? Option.some(visible(roots).size())
                : Option.none();
        return Pane.bordered(Messages.get("panel.files.title"), tree, ID.equals(focusedId), Math.max(0, tree.selected()), count);
    }

    /**
     * The tree widget, which holds the selection and expansion state.
     */
    TreeElement<FileTree> tree() {
        return tree;
    }

    @SuppressWarnings("unchecked")
    private void rebuild(Loadable<RepoStatus> status) {
        Set<String> collapsed = nodes(roots)
                .filter(n -> n.data() instanceof FileTree.Dir && !n.isExpanded())
                .map(n -> path(n.data()))
                .toSet();
        Option<String> selected = Option.of(tree.selectedNode())
                .flatMap(node -> Option.of(node.data()))
                .map(FilesPanel::path);

        roots = switch (status) {
            case Loadable.Loading<RepoStatus>() -> List.of(TreeNode.<FileTree>of(Placeholders.loading()).leaf());
            case Loadable.Failed<RepoStatus>(Failure failure) ->
                    List.of(TreeNode.<FileTree>of(Placeholders.error(failure)).leaf());
            case Loadable.Loaded<RepoStatus>(RepoStatus value) when value.files().isEmpty() -> List.of();
            case Loadable.Loaded<RepoStatus>(RepoStatus value) -> List.of(node(
                    new FileTree.Dir(ROOT_NAME, "", FileTree.of(value.files())), collapsed));
        };
        tree.roots(roots.toJavaArray(TreeNode[]::new));

        List<String> visible = visible(roots).map(n -> path(n.data()));
        int fallback = Math.min(Math.max(0, tree.selected()), Math.max(0, visible.size() - 1));
        tree.selected(selected.map(visible::indexOf).filter(i -> i >= 0).getOrElse(fallback));
    }

    private static TreeNode<FileTree> node(FileTree node, Set<String> collapsed) {
        return switch (node) {
            case FileTree.Dir dir -> {
                TreeNode<FileTree> parent = TreeNode.of(dir.name(), node).expanded(!collapsed.contains(dir.path()));
                dir.children().forEach(child -> parent.add(node(child, collapsed)));
                yield parent;
            }
            case FileTree.File file ->
                    TreeNode.of(file.entry().type().marker() + " " + file.name(), node).leaf();
        };
    }

    /**
     * Files show two status columns like {@code git status --short}: the staged change in green, then
     * the unstaged change in red. Untracked files show a red {@code ??}. Files in a merge conflict show a
     * {@code U} for each branch that updated them: green for the current branch, red for the other.
     */
    private static StyledElement<?> renderNode(TreeNode<FileTree> node) {
        if (!(node.data() instanceof FileTree.File file)) {
            return Toolkit.text(node.label());
        }
        FileEntry entry = file.entry();
        if (entry.type() == ChangeType.UNTRACKED) {
            return Toolkit.row(Toolkit.text("??").addClass(Theme.Classes.UNSTAGED), Toolkit.text(" " + file.name()));
        }
        String staged = entry.conflict().map(conflict -> marker(conflict.ours()))
                .getOrElse(() -> entry.staged() ? entry.type().marker() : " ");
        String unstaged = entry.conflict().map(conflict -> marker(conflict.theirs()))
                .getOrElse(() -> !entry.unstaged() ? " " : entry.type() == ChangeType.DELETED ? "D" : "M");
        return Toolkit.row(Toolkit.text(staged).addClass(Theme.Classes.STAGED),
                Toolkit.text(unstaged).addClass(Theme.Classes.UNSTAGED), Toolkit.text(" " + file.name()));
    }

    /**
     * {@code U} for a branch that updated the conflicting file, {@code D} for one that deleted it.
     */
    private static String marker(Conflict.Side side) {
        return side == Conflict.Side.DELETED ? "D" : "U";
    }

    /**
     * The path a node stands for, or {@code null} for a placeholder.
     */
    private static String path(FileTree node) {
        return switch (node) {
            case null -> null;
            case FileTree.Dir dir -> dir.path();
            case FileTree.File file -> file.entry().path();
        };
    }

    /**
     * All nodes, depth first.
     */
    private static List<TreeNode<FileTree>> nodes(List<TreeNode<FileTree>> nodes) {
        return nodes.flatMap(n -> nodes(List.ofAll(n.children())).prepend(n));
    }

    /**
     * The nodes shown on screen, in order: those not inside a collapsed directory.
     */
    private static List<TreeNode<FileTree>> visible(List<TreeNode<FileTree>> nodes) {
        return nodes.flatMap(n -> (n.isExpanded() ? visible(List.ofAll(n.children())) : List.<TreeNode<FileTree>>empty())
                .prepend(n));
    }
}
