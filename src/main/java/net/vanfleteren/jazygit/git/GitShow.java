package net.vanfleteren.jazygit.git;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * A single commit, as {@code git show} shows it.
 */
public interface GitShow {

    /**
     * What the commit {@code sha} changed, like {@code git show}: the changed files, a summary of the
     * changes, a blank line and the diff. Long output is cut off.
     */
    Either<ProcessError, DiffText> commitChanges(Sha sha);
}
