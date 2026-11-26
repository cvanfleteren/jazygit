package net.vanfleteren.jazygit.features.changes.panes.files;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.git.model.FileTree;
import net.vanfleteren.jazygit.core.git.model.Pathed;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.UnchangedNode;

/**
 * What the lists of the files pane, of the working tree and of a commit, tell of their nodes alike: how a file is
 * named, whether a node has changes, the directories to start collapsed, what highlighting a node without changes
 * shows (ADR 108, 128), and where the highlight goes as a list switches between its changed files and all of them.
 * Each list keeps one of its own.
 */
final class FileNodes {

    // How many files the directory without changes highlighted last holds, the node itself the key: counting them on
    // every frame would walk all files under the root.
    private Option<Counted> counted = Option.none();
    // What the files drawn last were of, and the tree they were drawn in, to see `a` switch to the other list.
    private Option<Listed> listed = Option.none();

    /**
     * Draws the files of {@code of} in {@code tree}: when they were drawn in {@code other} last, the tree of the other
     * list of them, the pane switched between their changed files and all of them, and the highlight goes with its
     * path.
     */
    <T extends Pathed> void switching(Object of, FileTreeList<T> tree, FileTreeList<T> other) {
        if (listed.exists(last -> last.of().equals(of) && last.tree() == other)) {
            other.selected().map(FileTree::path).forEach(path -> tree.carrying(of, path));
        }
        listed = Option.of(new Listed(of, tree));
    }

    /**
     * What highlighting {@code node}, without changes, asks to show: a file's content, or how many files a directory
     * holds.
     */
    <T extends Pathed> Option<Msg> unchangedSelection(FileTree<T> node) {
        return Option.of(
                node instanceof FileTree.File<T> file
                        ? new SelectionMsg.UnchangedFileSelected(file.path())
                        : new SelectionMsg.UnchangedDirectorySelected(node.path(), count(node)));
    }

    /**
     * How many files are under {@code node}, counted once for each node.
     */
    private int count(FileTree<?> node) {
        Counted known = counted.filter(last -> last.node() == node)
                .getOrElse(() -> new Counted(node, node.entries().size()));
        counted = Option.of(known);
        return known.files();
    }

    /**
     * Whether {@code shown} is what {@code msg} asks to show.
     */
    static boolean shows(Option<UnchangedNode> shown, Msg msg) {
        return shown.exists(node -> switch (node) {
            case UnchangedNode.File(var content) ->
                msg instanceof SelectionMsg.UnchangedFileSelected(String path)
                        && content.key().equals(path);
            case UnchangedNode.Directory(String path, int files) ->
                msg.equals(new SelectionMsg.UnchangedDirectorySelected(path, files));
        });
    }

    /**
     * The file name, preceded by where a renamed file came from: just the name when it stayed in the same
     * directory, the full path otherwise.
     */
    static String name(FileTree.File<?> file) {
        String path = file.entry().path();
        String directory = path.substring(0, path.length() - file.name().length());
        return file.entry()
                .origin()
                .map(from -> from.startsWith(directory) ? from.substring(directory.length()) : from)
                .map(from -> from + " -> " + file.name())
                .getOrElse(file.name());
    }

    /**
     * Whether a file under {@code node}, or it itself, is {@code changed}; it stops at the first one.
     */
    static <T extends Pathed> boolean hasChanges(FileTree<T> node, Predicate<T> changed) {
        return switch (node) {
            case FileTree.File<T> file -> changed.test(file.entry());
            case FileTree.Dir<T> dir -> dir.children().exists(child -> hasChanges(child, changed));
        };
    }

    /**
     * The paths of the directories of {@code files} with none {@code changed} under them, which a tree of all files
     * starts collapsed: of the working tree (ADR 108), or of a commit (ADR 128).
     */
    static <T extends Pathed> Set<String> unchangedDirectories(List<T> files, Predicate<T> changed) {
        return directories(FileTree.of(files))
                .filter(dir -> !hasChanges(dir, changed))
                .map(FileTree.Dir::path)
                .toSet();
    }

    private static <T extends Pathed> List<FileTree.Dir<T>> directories(List<FileTree<T>> nodes) {
        return nodes.flatMap(node -> node instanceof FileTree.Dir<T> dir
                ? directories(dir.children()).prepend(dir)
                : List.empty());
    }

    /**
     * How many files are under {@code node}.
     */
    private record Counted(FileTree<?> node, int files) {}

    /**
     * What the files drawn were of, and the tree they were drawn in: of their changed files, or of all of them.
     */
    private record Listed(Object of, FileTreeList<?> tree) {}
}
