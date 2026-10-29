package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.KeyEventHandler;
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
import java.util.function.Function;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.Pathed;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;

/**
 * Changed files as a directory tree under a root that holds them all, in a tree widget that keeps its own selection
 * and expansion. It is only rebuilt when the files change, and then keeps the collapsed directories and the selected
 * path, or the nearest one when that is gone; files of something else start afresh.
 *
 * @param <T> the files: those of the working tree, or those a commit changed
 */
final class FileTreeList<T extends Pathed> {

    // A virtual directory holding everything, so that all files can be acted on at once.
    private static final String ROOT_NAME = "/";
    // TamboUI's default, which indents every node.
    private static final String HIGHLIGHT_SYMBOL = "> ";

    private final TreeElement<FileTree<T>> tree;
    private final Function<FileTree.File<T>, String> label;
    private final Function<FileTree.File<T>, StyledElement<?>> drawFile;
    private final Theme.Highlights highlights;
    private List<TreeNode<FileTree<T>>> roots = List.of();
    // What the files are of, and the files, that the tree was built for.
    private Option<Object> shownOf = Option.none();
    private Option<Loadable<List<T>>> shown = Option.none();
    // The nodes drawn for the frame being rendered, with what they were drawn as, to find the one clicked on.
    private List<Drawn<T>> drawn = List.of();

    /**
     * @param id         the id of the pane the tree is drawn in
     * @param label      the label of the node of a file
     * @param drawFile   how the node of a file is drawn
     * @param highlights how the highlighted node is shown, also when the pane is not in use
     */
    FileTreeList(
            String id,
            Function<FileTree.File<T>, String> label,
            Function<FileTree.File<T>, StyledElement<?>> drawFile,
            Theme.Highlights highlights) {
        this.label = label;
        this.drawFile = drawFile;
        this.highlights = highlights;
        tree = Toolkit.<FileTree<T>>tree()
                .id(id)
                .focusable()
                .scrollbar(ScrollBarPolicy.AS_NEEDED)
                .nodeRenderer(this::drawNode);
        tree.onMouseEvent(this::click);
    }

    /**
     * Handles the keys typed while the tree is drawn with {@code handler}.
     */
    void onKey(KeyEventHandler handler) {
        tree.onKeyEvent(handler);
    }

    /**
     * The tree of {@code files}, which are those of {@code of}, rebuilt when they are not the ones it was built for.
     *
     * @param active whether the pane is in use, which shows the highlighted node
     */
    TreeElement<FileTree<T>> render(Object of, Loadable<List<T>> files, boolean active) {
        drawn = List.of();
        tree.highlightStyle(highlights.of(active))
                .highlightSymbol(active ? HIGHLIGHT_SYMBOL : " ".repeat(HIGHLIGHT_SYMBOL.length()));
        if (!shownOf.contains(of)) {
            shownOf = Option.of(of);
            shown = Option.of(files);
            roots = List.of();
            rebuild(files);
            tree.selected(0);
        } else if (!shown.contains(files)) {
            shown = Option.of(files);
            rebuild(files);
        }
        return tree;
    }

    /**
     * How many rows the tree shows once the files are read: expanding or collapsing a directory changes that without
     * a rebuild.
     */
    Option<Integer> count(Loadable<List<T>> files) {
        return files.toOption().map(read -> visible(roots).size());
    }

    /**
     * The row of the highlighted node.
     */
    int selectedRow() {
        return Math.max(0, tree.selected());
    }

    /**
     * The highlighted node, unless it is a placeholder.
     */
    Option<FileTree<T>> selected() {
        return Option.of(tree.selectedNode()).flatMap(node -> Option.of(node.data()));
    }

    /**
     * The tree widget, which holds the selection and expansion state.
     */
    TreeElement<FileTree<T>> tree() {
        return tree;
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
    private StyledElement<?> drawNode(TreeNode<FileTree<T>> node) {
        StyledElement<?> element =
                node.data() instanceof FileTree.File<T> file ? drawFile.apply(file) : Toolkit.text(node.label());
        drawn = drawn.prepend(new Drawn<>(node, element));
        return element;
    }

    private TreeNode<FileTree<T>> node(FileTree<T> node, Set<String> collapsed) {
        return switch (node) {
            case FileTree.Dir<T> dir -> {
                TreeNode<FileTree<T>> parent = TreeNode.of(dir.name(), node).expanded(!collapsed.contains(dir.path()));
                dir.children().forEach(child -> parent.add(node(child, collapsed)));
                yield parent;
            }
            case FileTree.File<T> file -> TreeNode.of(label.apply(file), node).leaf();
        };
    }

    /**
     * All nodes, depth first.
     */
    private static <T extends Pathed> List<TreeNode<FileTree<T>>> nodes(List<TreeNode<FileTree<T>>> nodes) {
        return nodes.flatMap(n -> nodes(List.ofAll(n.children())).prepend(n));
    }

    /**
     * The nodes shown on screen, in order: those not inside a collapsed directory.
     */
    private static <T extends Pathed> List<TreeNode<FileTree<T>>> visible(List<TreeNode<FileTree<T>>> nodes) {
        return nodes.flatMap(n ->
                (n.isExpanded() ? visible(List.ofAll(n.children())) : List.<TreeNode<FileTree<T>>>empty()).prepend(n));
    }

    @SuppressWarnings("unchecked")
    private void rebuild(Loadable<List<T>> files) {
        List<Option<String>> before = paths(visible(roots));
        int previous = tree.selected();
        roots = roots(files, collapsed(roots));
        tree.roots(roots.toJavaArray(TreeNode[]::new));
        tree.selected(restored(before, previous, paths(visible(roots))));
    }

    /**
     * The paths of the collapsed directories.
     */
    private static <T extends Pathed> Set<String> collapsed(List<TreeNode<FileTree<T>>> roots) {
        return nodes(roots)
                .filter(n -> !n.isExpanded())
                .flatMap(n -> n.data() instanceof FileTree.Dir<T> dir ? Option.of(dir.path()) : Option.none())
                .toSet();
    }

    /**
     * The tree's roots for {@code files}, keeping the {@code collapsed} directories collapsed.
     */
    private List<TreeNode<FileTree<T>>> roots(Loadable<List<T>> files, Set<String> collapsed) {
        return switch (files) {
            case Loadable.Loading<List<T>>() ->
                List.of(TreeNode.<FileTree<T>>of(Placeholders.loading()).leaf());
            case Loadable.Failed<List<T>>(Failure failure) ->
                List.of(TreeNode.<FileTree<T>>of(Placeholders.error(failure)).leaf());
            case Loadable.Loaded<List<T>>(List<T> value) when value.isEmpty() -> List.of();
            case Loadable.Loaded<List<T>>(List<T> value) ->
                List.of(node(new FileTree.Dir<>(ROOT_NAME, "", FileTree.of(value)), collapsed));
        };
    }

    /**
     * The path of each node; placeholders have none.
     */
    private static <T extends Pathed> List<Option<String>> paths(List<TreeNode<FileTree<T>>> nodes) {
        return nodes.map(n -> Option.of(n.data()).map(FileTree::path));
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
    private record Drawn<T extends Pathed>(TreeNode<FileTree<T>> node, StyledElement<?> element) {}
}
