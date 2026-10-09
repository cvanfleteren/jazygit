package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.widgets.tree.TreeNode;
import net.vanfleteren.jazygit.model.FileTree;
import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Left-side panel showing the changed files, as in {@code git status}, as a directory tree. The tree
 * widget keeps its own selection and expansion, so it is only rebuilt when the status changes, and
 * then keeps the collapsed directories and the selected path.
 */
public class FilesPanel {

    public static final String ID = "files";

    private final TreeElement<FileTree> tree = Toolkit.<FileTree>tree()
            .id(ID)
            .focusable();
    private List<TreeNode<FileTree>> roots = List.of();
    private Loadable<RepoStatus> shown;

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        if (model.status() != shown) {
            shown = model.status();
            rebuild(shown);
        }
        // Counted on every render: expanding or collapsing a directory changes the visible rows
        // without a rebuild.
        int count = shown instanceof Loadable.Loaded<RepoStatus> ? (int) visible(roots).count() : 0;
        return Pane.bordered("Files", tree, ID.equals(focusedId), Math.max(0, tree.selected()), count);
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
                .collect(Collectors.toUnmodifiableSet());
        Optional<String> selected = Optional.ofNullable(tree.selectedNode())
                .map(TreeNode::data)
                .map(FilesPanel::path);

        roots = switch (status) {
            case Loadable.Loading<RepoStatus>() -> List.of(TreeNode.<FileTree>of(Placeholders.LOADING).leaf());
            case Loadable.Failed<RepoStatus>(String message) ->
                    List.of(TreeNode.<FileTree>of(Placeholders.error(message)).leaf());
            case Loadable.Loaded<RepoStatus>(RepoStatus value) -> FileTree.of(value.files()).stream()
                    .map(node -> node(node, collapsed))
                    .toList();
        };
        tree.roots(roots.toArray(TreeNode[]::new));

        List<String> visible = visible(roots).map(n -> path(n.data())).toList();
        int fallback = Math.min(Math.max(0, tree.selected()), Math.max(0, visible.size() - 1));
        tree.selected(selected.map(visible::indexOf).filter(i -> i >= 0).orElse(fallback));
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
    private static Stream<TreeNode<FileTree>> nodes(List<TreeNode<FileTree>> nodes) {
        return nodes.stream().flatMap(n -> Stream.concat(Stream.of(n), nodes(n.children())));
    }

    /**
     * The nodes shown on screen, in order: those not inside a collapsed directory.
     */
    private static Stream<TreeNode<FileTree>> visible(List<TreeNode<FileTree>> nodes) {
        return nodes.stream().flatMap(n -> Stream.concat(Stream.of(n),
                n.isExpanded() ? visible(n.children()) : Stream.empty()));
    }
}
