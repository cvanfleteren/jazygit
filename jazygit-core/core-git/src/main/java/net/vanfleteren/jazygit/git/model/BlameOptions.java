package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * How a version of a file is blamed (ADR 147). A block moved within the file always keeps its author ({@code -M});
 * lines copied from other files are not looked for ({@code -C}), which can take minutes on a large repository.
 *
 * @param ignoreWhitespace whether whitespace is ignored when lines are compared, {@code -w}, as the diff options ignore
 *                         it in the diff: that changes whom a line is blamed on, never its text
 * @param lastLine         the last line blamed, of a version cut where its read was cut (ADR 79); none for all of them
 */
public record BlameOptions(boolean ignoreWhitespace, Option<Integer> lastLine) {

    /**
     * Every line blamed, whitespace not ignored.
     */
    public static final BlameOptions DEFAULT = new BlameOptions(false, Option.none());
}
