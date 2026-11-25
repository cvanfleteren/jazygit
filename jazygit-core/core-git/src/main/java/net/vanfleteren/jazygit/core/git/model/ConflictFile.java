package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;

/**
 * How a file in conflict stands against the conflict git wrote of it, which says where mergiraf can start from
 * (ADR 201): mergiraf reads conflicts with their base in them, which git writes again only by rewriting the file.
 */
public enum ConflictFile {

    /**
     * Every conflict in it has its base, as diff3 writes it: mergiraf reads it as it is, with what was edited.
     */
    WITH_BASE,

    /**
     * As git wrote it, but for the labels of its markers: writing it again with the bases loses nothing.
     */
    AS_WRITTEN,

    /**
     * Changed since git wrote it, as by merging some of its conflicts by hand: writing it again loses that.
     */
    EDITED;

    private static final String OURS = "<<<<<<< ";
    private static final String BASE = "||||||| ";
    private static final String THEIRS = ">>>>>>> ";
    private static final String SEPARATOR = "=======";

    /**
     * How {@code working}, the file of the working tree, stands against {@code written}, the conflict git writes of
     * it from the index: compared line by line, but for the labels after the markers, which say the same sides by
     * other names.
     */
    public static ConflictFile of(String working, String written) {
        if (withBase(working)) {
            return WITH_BASE;
        }
        return unlabelled(working).equals(unlabelled(written)) ? AS_WRITTEN : EDITED;
    }

    /**
     * Whether {@code content} has conflicts, and each has its base.
     */
    private static boolean withBase(String content) {
        List<String> lines = List.of(content.split("\n", -1));
        List<Integer> starts =
                lines.zipWithIndex().filter(line -> line._1().startsWith(OURS)).map(line -> line._2());
        return starts.nonEmpty()
                && starts.forAll(start -> lines.drop(start + 1)
                        .takeWhile(line -> !line.startsWith(SEPARATOR))
                        .exists(line -> line.startsWith(BASE)));
    }

    /**
     * The lines of {@code content} without the labels of their markers, nor the carriage return a checkout with CRLF
     * line endings ends them with, which the index does not have.
     */
    private static List<String> unlabelled(String content) {
        return List.of(content.split("\n", -1))
                .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
                .map(line -> line.startsWith(OURS) || line.startsWith(BASE) || line.startsWith(THEIRS)
                        ? line.substring(0, OURS.length() - 1)
                        : line);
    }
}
