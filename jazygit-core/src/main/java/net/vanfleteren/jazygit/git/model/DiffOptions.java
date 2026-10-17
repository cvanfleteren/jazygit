package net.vanfleteren.jazygit.git.model;

/**
 * How diffs are read.
 *
 * @param ignoreWhitespace whether changes to whitespace only are left out, as {@code git diff -w} does; a file
 *                         with nothing else changed is left out entirely
 */
public record DiffOptions(boolean ignoreWhitespace) {

    /**
     * Diffs as git shows them by default.
     */
    public static final DiffOptions DEFAULT = new DiffOptions(false);

    public DiffOptions withIgnoreWhitespace(boolean ignore) {
        return new DiffOptions(ignore);
    }
}
