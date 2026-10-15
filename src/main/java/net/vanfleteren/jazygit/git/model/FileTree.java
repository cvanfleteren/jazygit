package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Changed files arranged as a directory tree. A directory whose only child is another directory is
 * merged with it, so a chain like {@code src/main/java} shows as a single node.
 */
public sealed interface FileTree {

    /**
     * The roots of the tree for the given files: directories first, then files, each sorted by name.
     */
    static List<FileTree> of(List<FileEntry> files) {
        return children("", files);
    }

    /**
     * The nodes directly below the directory {@code prefix} (empty, or ending in {@code /}), which all
     * {@code files} are inside of.
     */
    private static List<FileTree> children(String prefix, List<FileEntry> files) {
        var nested = files.partition(f -> relative(prefix, f).contains("/"));
        List<FileTree> dirs = nested._1
                .groupBy(f -> relative(prefix, f).split("/", 2)[0])
                .toList()
                .sortBy(entry -> entry._1)
                .map(entry ->
                        merged(new Dir(entry._1, prefix + entry._1, children(prefix + entry._1 + "/", entry._2))));
        List<FileTree> leaves = nested._2
                .<FileTree>map(f -> new File(relative(prefix, f), f))
                .sorted((a, b) -> a.name().compareTo(b.name()));
        return dirs.appendAll(leaves);
    }

    private static String relative(String prefix, FileEntry file) {
        return file.path().substring(prefix.length());
    }

    /**
     * Merges a directory with its only child directory. The child was already merged with its own
     * descendants, so one step is enough.
     */
    private static FileTree merged(Dir dir) {
        return dir.children()
                .headOption()
                .filter(only -> dir.children().size() == 1)
                .flatMap(only -> only instanceof Dir child ? Option.of(child) : Option.none())
                .<FileTree>map(child -> new Dir(dir.name() + "/" + child.name(), child.path(), child.children()))
                .getOrElse(dir);
    }

    /**
     * The label of this node: the file name, or the (possibly merged) directory names joined by {@code /}.
     */
    String name();

    /**
     * All files in this node: the file itself, or every file below a directory.
     */
    default List<FileEntry> entries() {
        return switch (this) {
            case File file -> List.of(file.entry());
            case Dir dir -> dir.children().flatMap(FileTree::entries);
        };
    }

    /**
     * The files shown in the diff panel: the file itself, or only the files directly inside a directory,
     * not those in its subdirectories.
     */
    default List<FileEntry> directEntries() {
        return switch (this) {
            case File file -> List.of(file.entry());
            case Dir dir ->
                dir.children().flatMap(child -> child instanceof File file ? List.of(file.entry()) : List.empty());
        };
    }

    /**
     * @param path the full repository-relative path of the directory
     */
    record Dir(String name, String path, List<FileTree> children) implements FileTree {}

    record File(String name, FileEntry entry) implements FileTree {}
}
