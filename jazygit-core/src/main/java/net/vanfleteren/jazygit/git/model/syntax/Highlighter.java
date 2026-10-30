package net.vanfleteren.jazygit.git.model.syntax;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;

/**
 * What colours the code of a diff by its syntax (ADR 53).
 */
public interface Highlighter {

    /**
     * For each hunk of {@code file}, the tokens of each of its lines, or none when the hunk is not lexed: when the
     * language of the file is not known, the file is conflicted or the lines of the hunk are not numbered. A line of
     * a hunk that is lexed has none when it has no code, or no code that is coloured.
     */
    List<Option<List<List<Token>>>> tokens(FileDiff file);
}
