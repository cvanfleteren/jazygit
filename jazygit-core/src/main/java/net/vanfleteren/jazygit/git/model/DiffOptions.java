package net.vanfleteren.jazygit.git.model;

import lombok.With;

/**
 * How diffs are read.
 *
 * @param ignoreWhitespace whether changes to whitespace only are left out, as {@code git diff -w} does; a file
 *                         with nothing else changed is left out entirely
 * @param firstParent      whether what a merge commit changed is read against its first parent only, as
 *                         {@code --diff-merges=first-parent} does, rather than as git's combined diff
 */
@With
public record DiffOptions(boolean ignoreWhitespace, boolean firstParent) {

    /**
     * Diffs as git shows them by default.
     */
    public static final DiffOptions DEFAULT = new DiffOptions(false, false);
}
