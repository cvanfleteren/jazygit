package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * A file with conflict markers left in it, and the lines its blocks start at, counted from 1 (ADR 198).
 *
 * @param path  the path of the file in the working tree
 * @param lines the line of each {@code <<<<<<< } that a {@code >>>>>>> } closes, in order
 */
public record MarkedFile(RepoPath path, List<Integer> lines) {

    private static final String START = "<<<<<<< ";
    private static final String END = ">>>>>>> ";

    /**
     * The lines that the blocks of conflict markers in {@code text} start at, as git writes them: a line that starts
     * with {@code <<<<<<< }, closed by a later one that starts with {@code >>>>>>> }. A {@code =======} alone, as
     * underlines a heading in Markdown, is no block.
     */
    public static List<Integer> blockStarts(String text) {
        List<String> lines = List.of(text.split("\n", -1));
        return lines.zipWithIndex()
                .filter(line -> line._1().startsWith(START))
                .map(line -> line._2())
                .filter(start -> lines.drop(start + 1)
                        .takeWhile(line -> !line.startsWith(START))
                        .exists(line -> line.startsWith(END)))
                .map(start -> start + 1);
    }
}
