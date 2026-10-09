package net.vanfleteren.jazygit.model;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Changed files arranged as a directory tree. A directory whose only child is another directory is
 * merged with it, so a chain like {@code src/main/java} shows as a single node.
 */
public sealed interface FileTree {

    /**
     * The label of this node: the file name, or the (possibly merged) directory names joined by {@code /}.
     */
    String name();

    /**
     * @param path the full repository-relative path of the directory
     */
    record Dir(String name, String path, List<FileTree> children) implements FileTree {

        public Dir {
            children = List.copyOf(children);
        }
    }

    record File(String name, FileEntry entry) implements FileTree {
    }

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
        Map<Boolean, List<FileEntry>> nested = files.stream()
                .collect(Collectors.partitioningBy(f -> relative(prefix, f).contains("/")));
        Stream<FileTree> dirs = nested.get(true).stream()
                .collect(Collectors.groupingBy(f -> relative(prefix, f).split("/", 2)[0], TreeMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(e -> merged(new Dir(e.getKey(), prefix + e.getKey(),
                        children(prefix + e.getKey() + "/", e.getValue()))));
        Stream<FileTree> leaves = nested.get(false).stream()
                .map(f -> (FileTree) new File(relative(prefix, f), f))
                .sorted((a, b) -> a.name().compareTo(b.name()));
        return Stream.concat(dirs, leaves).toList();
    }

    private static String relative(String prefix, FileEntry file) {
        return file.path().substring(prefix.length());
    }

    /**
     * Merges a directory with its only child directory. The child was already merged with its own
     * descendants, so one step is enough.
     */
    private static FileTree merged(Dir dir) {
        if (dir.children().size() == 1 && dir.children().getFirst() instanceof Dir child) {
            return new Dir(dir.name() + "/" + child.name(), child.path(), child.children());
        }
        return dir;
    }
}
