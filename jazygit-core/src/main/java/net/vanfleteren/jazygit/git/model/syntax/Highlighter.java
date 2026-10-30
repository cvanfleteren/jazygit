package net.vanfleteren.jazygit.git.model.syntax;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;

/**
 * What colours the code of a diff by its syntax (ADR 53).
 */
public interface Highlighter {

    /**
     * The tokens of each line of each hunk of {@code file}, in the shape of its hunks: none for a line that is not
     * code, and none at all for a file of no language known.
     */
    List<List<List<Token>>> tokens(FileDiff file);
}
