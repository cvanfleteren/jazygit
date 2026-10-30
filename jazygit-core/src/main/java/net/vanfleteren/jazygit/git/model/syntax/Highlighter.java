package net.vanfleteren.jazygit.git.model.syntax;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.syntax.Token;

/**
 * What colours the code of a diff by its syntax (ADR 53).
 */
public interface Highlighter {

    /**
     * The tokens of each line of each hunk of {@code file}, in the shape of its hunks, or none for a line that is not
     * lexed: every line when the language of the file is not known, the file is conflicted or the lines of the hunk
     * are not numbered, and a note, or a line too long to be lexed. A line that is lexed has no tokens when it has no
     * code that is coloured.
     */
    List<List<Option<List<Token>>>> tokens(FileDiff file);
}
