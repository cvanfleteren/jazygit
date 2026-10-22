package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.tree.TreeNode;
import io.vavr.Tuple;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * Left-side panel showing the changed files, as in {@code git status}, as a directory tree. The tree
 * widget keeps its own selection and expansion, so it is only rebuilt when the status changes, and
 * then keeps the collapsed directories and the selected path, or the nearest one when that is gone.
 */
public class FilesPanel {

    public static final String ID = Scopes.FILES.id();

    // A virtual directory holding everything, so that all changes can be (un)staged at once.
    private static final String ROOT_NAME = "/";
    // TamboUI's default, which indents every node.
    private static final String HIGHLIGHT_SYMBOL = "> ";

    private final TreeElement<FileTree> tree = Toolkit.<FileTree>tree()
            .id(ID)
            .focusable()
            .scrollbar(ScrollBarPolicy.AS_NEEDED)
            .nodeRenderer(this::drawNode);
    private final Keymap keymap = Keymap.standard();
    private final Theme.Highlights highlights;
    private List<TreeNode<FileTree>> roots = List.of();
    // The status the tree was built for.
    private Option<Loadable<RepoStatus>> shown = Option.none();
    // The tree's key handler also sees keys typed in other panes, so it must know whether it has focus.
    private boolean focused;
    // The nodes drawn for the frame being rendered, with what they were drawn as, to find the one clicked on.
    private List<Drawn> drawn = List.of();

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
        this.highlights = highlights;
        tree.onKeyEvent(PaneKeys.whileFocused(
                () -> focused,
                event -> event.code() == KeyCode.ENTER && selected().isDefined()
                        ? Option.of(openDiff)
                        : PaneKeys.sending(keymap.handle(Scopes.FILES, event, selected()), dispatch)));
        tree.onMouseEvent(this::click);
    }

    /**
     * A left click highlights the node clicked on, as moving to it with the keys does; the app has already
     * given the pane the focus.
     */
    private EventResult click(MouseEvent event) {
        if (event.kind() != MouseEventKind.PRESS || !event.isLeftButton()) {
            return EventResult.UNHANDLED;
        }
        return drawn.find(d -> {
                    Rect area = d.element().renderedArea();
                    return area != null && event.y() >= area.top() && event.y() < area.bottom();
                })
                .map(d -> visible(roots).indexOf(d.node()))
                .filter(index -> index >= 0)
                .map(index -> {
                    tree.selected(index);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * Draws {@code node}, remembering where, for {@link #click}.
     */
    private StyledElement<?> drawNode(TreeNode<FileTree> node) {
        StyledElement<?> element = renderNode(node);
        drawn = drawn.prepend(new Drawn(node, element));
        return element;
    }

    private static TreeNode<FileTree> node(FileTree node, Set<String> collapsed) {
        return switch (node) {
            case FileTree.Dir dir -> {
                TreeNode<FileTree> parent = TreeNode.of(dir.name(), node).expanded(!collapsed.contains(dir.path()));
                dir.children().forEach(child -> parent.add(node(child, collapsed)));
                yield parent;
            }
            case FileTree.File file ->
                TreeNode.of(file.entry().type().marker() + " " + name(file), node)
                        .leaf();
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
    private static String name(FileTree.File file) {
        String path = file.entry().path();
        String directory = path.substring(0, path.length() - file.name().length());
        return file.entry()
                .origin()
                .map(origin -> origin.startsWith(directory) ? origin.substring(directory.length()) : origin)
                .map(origin -> origin + " -> " + file.name())
                .getOrElse(file.name());
    }

    /**
     * {@code U} for a branch that updated the conflicting file, {@code D} for one that deleted it.
     */
    private static String marker(Conflict.Side side) {
        return side == Conflict.Side.DELETED ? "D" : "U";
    }

    /**
     * The path a node stands for.
     */
    private static String path(FileTree node) {
        return switch (node) {
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
        return nodes.flatMap(n ->
                (n.isExpanded() ? visible(List.ofAll(n.children())) : List.<TreeNode<FileTree>>empty()).prepend(n));
    }

    /**
     * The highlighted node, unless it is a placeholder.
     */
    private Option<FileTree> selected() {
        return Option.of(tree.selectedNode()).flatMap(node -> Option.of(node.data()));
    }

    /**
     * The message to send when the highlighted node is not the one whose diff {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return PaneKeys.selectionChange(
                selected().map(FileTree::directEntries), model.repoView().fileDiff(), SelectionMsg.FilesSelected::new);
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
        drawn = List.of();
        boolean active = ID.equals(activeId);
        tree.highlightStyle(highlights.of(active))
                .highlightSymbol(active ? HIGHLIGHT_SYMBOL : " ".repeat(HIGHLIGHT_SYMBOL.length()));
        if (!shown.contains(model.repo().status())) {
            shown = Option.of(model.repo().status());
            rebuild(model.repo().status());
        }
        // Counted on every render: expanding or collapsing a directory changes the visible rows
        // without a rebuild.
        Option<Integer> count =
                model.repo().status().toOption().map(status -> visible(roots).size());
        return Pane.bordered(Messages.get("panel.files.title"), tree, focused, Math.max(0, tree.selected()), count);
    }

    /**
     * The tree widget, which holds the selection and expansion state.
     */
    TreeElement<FileTree> tree() {
        return tree;
    }

    @SuppressWarnings("unchecked")
    private void rebuild(Loadable<RepoStatus> status) {
        List<Option<String>> before = paths(visible(roots));
        int previous = tree.selected();
        roots = roots(status, collapsed(roots));
        tree.roots(roots.toJavaArray(TreeNode[]::new));
        tree.selected(restored(before, previous, paths(visible(roots))));
    }

    /**
     * The paths of the collapsed directories.
     */
    private static Set<String> collapsed(List<TreeNode<FileTree>> roots) {
        return nodes(roots)
                .filter(n -> !n.isExpanded())
                .flatMap(n -> n.data() instanceof FileTree.Dir dir ? Option.of(dir.path()) : Option.none())
                .toSet();
    }

    /**
     * The tree's roots for {@code status}, keeping the {@code collapsed} directories collapsed.
     */
    private static List<TreeNode<FileTree>> roots(Loadable<RepoStatus> status, Set<String> collapsed) {
        return switch (status) {
            case Loadable.Loading<RepoStatus>() ->
                List.of(TreeNode.<FileTree>of(Placeholders.loading()).leaf());
            case Loadable.Failed<RepoStatus>(Failure failure) ->
                List.of(TreeNode.<FileTree>of(Placeholders.error(failure)).leaf());
            case Loadable.Loaded<RepoStatus>(RepoStatus value)
            when value.files().isEmpty() -> List.of();
            case Loadable.Loaded<RepoStatus>(RepoStatus value) ->
                List.of(node(new FileTree.Dir(ROOT_NAME, "", FileTree.of(value.files())), collapsed));
        };
    }

    /**
     * The path of each node; placeholders have none.
     */
    private static List<Option<String>> paths(List<TreeNode<FileTree>> nodes) {
        return nodes.map(n -> Option.of(n.data()).map(FilesPanel::path));
    }

    /**
     * The row of {@code after} to select when row {@code previous} of {@code before} was: the same path, or
     * else the nearest one still there, below before above, or else the same row, kept inside the tree.
     */
    private static int restored(List<Option<String>> before, int previous, List<Option<String>> after) {
        Vector<Option<String>> rows = before.toVector();
        Map<String, Integer> rowOf = after.zipWithIndex()
                .flatMap(row -> row._1.map(path -> Tuple.of(path, row._2)))
                .toMap(row -> row);
        return around(previous, rows.size())
                .flatMap(rows::get)
                .flatMap(rowOf::get)
                .headOption()
                .getOrElse(() -> clamp(previous, after.size()));
    }

    /**
     * Row {@code previous}, then the other of {@code size} rows, nearest first, the one below before the one
     * above.
     */
    private static Iterator<Integer> around(int previous, int size) {
        return Iterator.rangeClosed(0, 2 * (size + Math.abs(previous)))
                .map(k -> k % 2 == 1 ? previous + (k + 1) / 2 : previous - k / 2)
                .filter(i -> i >= 0 && i < size);
    }

    /**
     * {@code index}, kept between the first and last of {@code size} rows; 0 when there are none.
     */
    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size - 1));
    }

    /**
     * A node, and the element it was drawn as.
     */
    private record Drawn(TreeNode<FileTree> node, StyledElement<?> element) {}
}
