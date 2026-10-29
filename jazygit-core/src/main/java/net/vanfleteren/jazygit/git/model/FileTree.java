package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Changed files arranged as a directory tree. A directory whose only child is another directory is
 * merged with it, so a chain like {@code src/main/java} shows as a single node.
 *
 * @param <T> the files: those of the working tree, or those a commit changed
 */
public sealed interface FileTree<T extends Pathed> {

    /**
     * The roots of the tree for the given files: files first, then directories, each sorted by name.
     */
    static <T extends Pathed> List<FileTree<T>> of(List<T> files) {
        return children("", files);
    }

    /**
     * The nodes directly below the directory {@code prefix} (empty, or ending in {@code /}), which all
     * {@code files} are inside of.
     */
    private static <T extends Pathed> List<FileTree<T>> children(String prefix, List<T> files) {
        var nested = files.partition(f -> relative(prefix, f).contains("/"));
        List<FileTree<T>> dirs = nested._1
                .groupBy(f -> relative(prefix, f).split("/", 2)[0])
                .toList()
                .sortBy(entry -> entry._1)
                .map(entry ->
                        merged(new Dir<>(entry._1, prefix + entry._1, children(prefix + entry._1 + "/", entry._2))));
        List<FileTree<T>> leaves = nested._2
                .<FileTree<T>>map(f -> new File<>(relative(prefix, f), f))
                .sorted((a, b) -> a.name().compareTo(b.name()));
        return leaves.appendAll(dirs);
    }

    private static String relative(String prefix, Pathed file) {
        return file.path().substring(prefix.length());
    }

    /**
     * Merges a directory with its only child directory. The child was already merged with its own
     * descendants, so one step is enough.
     */
    private static <T extends Pathed> FileTree<T> merged(Dir<T> dir) {
        return dir.children()
                .headOption()
                .filter(only -> dir.children().size() == 1)
                .flatMap(only -> only instanceof Dir<T> child ? Option.of(child) : Option.none())
                .<FileTree<T>>map(child -> new Dir<>(dir.name() + "/" + child.name(), child.path(), child.children()))
                .getOrElse(dir);
    }

    /**
     * The label of this node: the file name, or the (possibly merged) directory names joined by {@code /}.
     */
    String name();

    /**
     * The repository-relative path of this node: of the directory, or of the file.
     */
    String path();

    /**
     * All files in this node: the file itself, or every file below a directory.
     */
    default List<T> entries() {
        return switch (this) {
            case File<T> file -> List.of(file.entry());
            case Dir<T> dir -> dir.children().flatMap(FileTree::entries);
        };
    }

    /**
     * The files shown in the diff panel: the file itself, or only the files directly inside a directory,
     * not those in its subdirectories.
     */
    default List<T> directEntries() {
        return switch (this) {
            case File<T> file -> List.of(file.entry());
            case Dir<T> dir ->
                dir.children().flatMap(child -> child instanceof File<T> file ? List.of(file.entry()) : List.empty());
        };
    }

    /**
     * @param path the full repository-relative path of the directory
     */
    record Dir<T extends Pathed>(String name, String path, List<FileTree<T>> children) implements FileTree<T> {}

    record File<T extends Pathed>(String name, T entry) implements FileTree<T> {

        @Override
        public String path() {
            return entry.path();
        }
    }
}
