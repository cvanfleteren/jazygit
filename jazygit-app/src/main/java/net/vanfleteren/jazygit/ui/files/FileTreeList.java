package net.vanfleteren.jazygit.ui.files;

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
import io.vavr.collection.HashSet;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.Pathed;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.view.FileStepMsg;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.BorderBar;
import net.vanfleteren.jazygit.ui.widgets.ListFilter;

/**
 * Changed files as a directory tree under a root that holds them all, in a tree widget that keeps its own selection
 * and expansion. It is only rebuilt when the files change, and then keeps the collapsed directories and the selected
 * path, or the nearest one when that is gone. Files of something else, or read again after a load, start afresh on
 * the row the tree is given to start on.
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
    private final ToIntFunction<List<TreeNode<FileTree<T>>>> start;
    private final Function<List<T>, Set<String>> startCollapsed;
    private List<TreeNode<FileTree<T>>> roots = List.of();
    // Whether the files have not been read since they started afresh, so the directories to start collapsed are
    // still to collapse.
    private boolean fresh;
    // What the files are of, and the files, that the tree was built for.
    private Option<Object> shownOf = Option.none();
    private Option<Loadable<List<T>>> shown = Option.none();
    // What the tree says while the files are read, and when there are none, that it was built for.
    private Placeholder placeholder = Placeholder.DEFAULT;
    // What the files were filtered on when the tree was built, and the directories collapsed before they were.
    private Option<String> filter = Option.none();
    private Set<String> collapsedUnfiltered = HashSet.empty();
    // Whether the files have not been read since they were last loading, so the start highlight is still to place.
    private boolean unread;
    // The nodes drawn for the frame being rendered, with what they were drawn as, to find the one clicked on.
    private List<Drawn<T>> drawn = List.of();
    // The node a step moved the highlight to (ADR 117), until the tree is drawn again: the widget says which node is
    // highlighted from the rows it last drew, which a directory opened on the way changed.
    private Option<TreeNode<FileTree<T>>> steppedTo = Option.none();

    /**
     * @param id         the id of the pane the tree is drawn in
     * @param label      the label of the node of a file
     * @param drawFile   how the node of a file is drawn
     * @param highlights how the highlighted node is shown, also when the pane is not in use
     * @param start      the row to highlight among the rows shown, once files start afresh: {@link #first} or
     *                   {@link #firstWithDiff}
     */
    FileTreeList(
            String id,
            Function<FileTree.File<T>, String> label,
            Function<FileTree.File<T>, StyledElement<?>> drawFile,
            Theme.Highlights highlights,
            ToIntFunction<List<TreeNode<FileTree<T>>>> start) {
        this(id, label, drawFile, highlights, start, files -> HashSet.empty());
    }

    /**
     * @param startCollapsed the paths of the directories to collapse once files start afresh and are read: a tree of
     *                       all files of the working tree opens only on its changes (ADR 108)
     */
    FileTreeList(
            String id,
            Function<FileTree.File<T>, String> label,
            Function<FileTree.File<T>, StyledElement<?>> drawFile,
            Theme.Highlights highlights,
            ToIntFunction<List<TreeNode<FileTree<T>>>> start,
            Function<List<T>, Set<String>> startCollapsed) {
        this.label = label;
        this.drawFile = drawFile;
        this.highlights = highlights;
        this.start = start;
        this.startCollapsed = startCollapsed;
        tree = Toolkit.<FileTree<T>>tree()
                .id(id)
                .focusable()
                .scrollbar(ScrollBarPolicy.AS_NEEDED)
                .scrollbarThumbColor(BorderBar.FOUND)
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
        return render(of, files, active, Placeholder.DEFAULT);
    }

    /**
     * The tree of {@code files}, which are those of {@code of}, rebuilt when they are not the ones it was built for,
     * saying what {@code placeholder} says while they are read or when there are none.
     *
     * @param active whether the pane is in use, which shows the highlighted node
     */
    TreeElement<FileTree<T>> render(Object of, Loadable<List<T>> files, boolean active, Placeholder placeholder) {
        return render(of, files, active, placeholder, Option.none());
    }

    /**
     * The tree of those of {@code files} that match {@code filter} (ADR 105). While they are filtered, every
     * directory is open; a change to the filter opens them all again, and clearing it collapses those collapsed
     * before. The highlight then stays on its path, or else goes to the first file shown.
     *
     * @param filter what the files are filtered on, if anything
     */
    TreeElement<FileTree<T>> render(
            Object of, Loadable<List<T>> files, boolean active, Placeholder placeholder, Option<String> filter) {
        drawn = List.of();
        tree.highlightStyle(highlights.of(active))
                .highlightSymbol(active ? HIGHLIGHT_SYMBOL : " ".repeat(HIGHLIGHT_SYMBOL.length()));
        if (!shownOf.contains(of)) {
            shownOf = Option.of(of);
            shown = Option.of(files);
            this.placeholder = placeholder;
            this.filter = filter;
            collapsedUnfiltered = HashSet.empty();
            roots = List.of();
            fresh = true;
            rebuild(files, started(files));
            tree.selected(0);
            unread = true;
        } else if (fresh && files instanceof Loadable.Loaded<List<T>> && !shown.contains(files)) {
            shown = Option.of(files);
            this.placeholder = placeholder;
            this.filter = filter;
            rebuild(files, started(files));
        } else if (!this.filter.equals(filter)) {
            Option<String> before = this.filter;
            shown = Option.of(files);
            this.placeholder = placeholder;
            this.filter = filter;
            refilter(files, before);
        } else if (!shown.contains(files) || !this.placeholder.equals(placeholder)) {
            shown = Option.of(files);
            this.placeholder = placeholder;
            rebuild(files, collapsed(roots));
        }
        // A load already lost the highlight, so it is placed afresh once the files are read again.
        if (!(files instanceof Loadable.Loaded<List<T>>)) {
            unread = true;
        } else if (unread) {
            unread = false;
            steppedTo = Option.none();
            tree.selected(start.applyAsInt(visible(roots)));
        }
        return tree;
    }

    /**
     * The directories to collapse as {@code files} start afresh: those {@link #startCollapsed} says once they are read,
     * which ends their start. While a filter is on, all are open, and those are collapsed once it is cleared.
     */
    private Set<String> started(Loadable<List<T>> files) {
        Set<String> collapsed = files.toOption().map(startCollapsed).getOrElse(HashSet.empty());
        fresh = !(files instanceof Loadable.Loaded<List<T>>);
        if (filter.isDefined()) {
            collapsedUnfiltered = collapsed;
            return HashSet.empty();
        }
        return collapsed;
    }

    /**
     * The first of the rows: the root.
     */
    static <T extends Pathed> int first(List<TreeNode<FileTree<T>>> rows) {
        return 0;
    }

    /**
     * The row of the first directory of {@code rows} that holds files directly, whose diff shows something, or else
     * the first row.
     */
    static <T extends Pathed> int firstWithDiff(List<TreeNode<FileTree<T>>> rows) {
        return Math.max(
                0,
                rows.indexWhere(n -> n.data() instanceof FileTree.Dir<T> dir
                        && !dir.directEntries().isEmpty()));
    }

    /**
     * How many rows the tree shows once the files are read: expanding or collapsing a directory changes that without
     * a rebuild.
     */
    Option<Integer> count(Loadable<List<T>> files) {
        return files.toOption()
                .map(read ->
                        matching(read, filter).isEmpty() ? 0 : visible(roots).size());
    }

    /**
     * Those of {@code files} whose path, or the path a renamed one came from, contains {@code filter}: all of them
     * when there is none.
     */
    static <T extends Pathed> List<T> matching(List<T> files, Option<String> filter) {
        return filter.map(typed -> files.filter(file -> ListFilter.matches(Option.of(typed), file.path())
                        || file.origin().exists(origin -> ListFilter.matches(Option.of(typed), origin))))
                .getOrElse(files);
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
        return selectedNode().flatMap(node -> Option.of(node.data()));
    }

    /**
     * The highlighted node: the one a step moved the highlight to until the tree is drawn again, else the widget's.
     */
    private Option<TreeNode<FileTree<T>>> selectedNode() {
        return steppedTo.orElse(() -> Option.of(tree.selectedNode()));
    }

    /**
     * Whether the highlighted node is a collapsed directory.
     */
    boolean collapsedSelected() {
        return selectedNode().exists(node -> node.data() instanceof FileTree.Dir && !node.isExpanded());
    }

    /**
     * Moves the highlight to the file {@code step} of the highlighted node, among all files of the tree in its order,
     * those in a collapsed directory too, and opens the directories leading to it; says whether there was one to move
     * to (ADR 117). The directories stay open, as when they are opened by hand.
     */
    boolean step(FileStepMsg.Step step) {
        List<TreeNode<FileTree<T>>> all = nodes(roots);
        int at = selectedNode().map(node -> all.indexWhere(n -> n == node)).getOrElse(-1);
        return stepped(all.map(TreeNode::data), at, step)
                .map(all::get)
                .peek(this::highlight)
                .isDefined();
    }

    /**
     * The index in {@code nodes}, all nodes of a tree depth first, of the file {@code step} of the node at {@code at}:
     * the first file after it, or the last before it. A directory's first file comes after it. None when there is
     * none; with nothing highlighted, {@code at} is -1, and the next file is the first.
     */
    static Option<Integer> stepped(List<? extends FileTree<?>> nodes, int at, FileStepMsg.Step step) {
        int found = step == FileStepMsg.Step.NEXT
                ? nodes.indexWhere(FileTree.File.class::isInstance, at + 1)
                : at <= 0 ? -1 : nodes.lastIndexWhere(FileTree.File.class::isInstance, at - 1);
        return Option.when(found >= 0, found);
    }

    /**
     * Highlights {@code node}, opening the directories that hold it.
     */
    private void highlight(TreeNode<FileTree<T>> node) {
        String path = node.data().path();
        nodes(roots)
                .filter(n -> n.data() instanceof FileTree.Dir<T> dir
                        && (dir.path().isEmpty() || path.startsWith(dir.path() + "/")))
                .forEach(n -> n.expanded(true));
        tree.selected(visible(roots).indexWhere(n -> n == node));
        steppedTo = Option.of(node);
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
                    steppedTo = Option.none();
                    tree.selected(index);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * Draws {@code node}, remembering where, for {@link #click}.
     */
    private StyledElement<?> drawNode(TreeNode<FileTree<T>> node) {
        // Drawn again, the widget knows the rows a step opened.
        steppedTo = Option.none();
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

    /**
     * Rebuilds the tree, keeping the {@code collapsed} directories collapsed, and the highlight on the same path or
     * the nearest one still there.
     */
    private void rebuild(Loadable<List<T>> files, Set<String> collapsed) {
        List<Option<String>> before = paths(visible(roots));
        int previous = tree.selected();
        show(roots(files, collapsed));
        tree.selected(restored(before, previous, paths(visible(roots))));
    }

    /**
     * Rebuilds the tree once the filter changed from {@code before}: with every directory open while it filters, or
     * else those collapsed before it did collapsed again. The highlight stays on the same path. Once the filter is
     * cleared, a path inside a directory collapsed again gives it to that directory; else it goes to the first file.
     */
    private void refilter(Loadable<List<T>> files, Option<String> before) {
        Option<String> highlighted = selected().map(FileTree::path);
        if (before.isEmpty()) {
            collapsedUnfiltered = collapsed(roots);
        }
        show(roots(files, filter.isDefined() ? HashSet.empty() : collapsedUnfiltered));
        List<TreeNode<FileTree<T>>> rows = visible(roots);
        List<Option<String>> shown = paths(rows);
        int same = shown.indexWhere(path -> path.isDefined() && path.equals(highlighted));
        int row = same >= 0 || filter.isDefined()
                ? same
                : highlighted.map(path -> holding(shown, path)).getOrElse(-1);
        tree.selected(row >= 0 ? row : Math.max(0, rows.indexWhere(n -> n.data() instanceof FileTree.File<T>)));
    }

    /**
     * The row of the deepest of the directories {@code shown} that holds {@code path}; -1 when none does.
     */
    private static int holding(List<Option<String>> shown, String path) {
        return shown.zipWithIndex()
                .filter(row -> row._1.exists(dir -> dir.isEmpty() || path.startsWith(dir + "/")))
                .maxBy(row -> row._1.get().length())
                .map(row -> row._2)
                .getOrElse(-1);
    }

    @SuppressWarnings("unchecked")
    private void show(List<TreeNode<FileTree<T>>> roots) {
        steppedTo = Option.none();
        this.roots = roots;
        tree.roots(roots.toJavaArray(TreeNode[]::new));
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
                List.of(TreeNode.<FileTree<T>>of(placeholder.loading()).leaf());
            case Loadable.Failed<List<T>>(Failure failure) ->
                List.of(TreeNode.<FileTree<T>>of(Placeholders.error(failure)).leaf());
            case Loadable.Loaded<List<T>>(List<T> value)
            when value.isEmpty() ->
                placeholder
                        .empty()
                        .map(empty -> TreeNode.<FileTree<T>>of(empty).leaf())
                        .toList();
            case Loadable.Loaded<List<T>>(List<T> value)
            when matching(value, filter).isEmpty() ->
                List.of(TreeNode.<FileTree<T>>of(Messages.get("panel.files.noMatches"))
                        .leaf());
            case Loadable.Loaded<List<T>>(List<T> value) ->
                List.of(node(new FileTree.Dir<>(ROOT_NAME, "", FileTree.of(matching(value, filter))), collapsed));
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
     * What a tree says in place of its files.
     *
     * @param loading what it says while they are read
     * @param empty   what it says when there are none; nothing when it shows nothing then
     */
    record Placeholder(String loading, Option<String> empty) {

        static final Placeholder DEFAULT = new Placeholder(Placeholders.loading(), Option.none());
    }

    /**
     * A node, and the element it was drawn as.
     */
    private record Drawn<T extends Pathed>(TreeNode<FileTree<T>> node, StyledElement<?> element) {}
}
