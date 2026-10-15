package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;

/**
 * The history, as {@code git log} shows it.
 */
public interface GitLog {

    /**
     * The log of HEAD, newest first.
     */
    Either<ProcessError, List<Commit>> commits();

    /**
     * The log of the branch {@code branch}, local or remote-tracking, newest first.
     */
    Either<LogError, List<Commit>> log(BranchRef branch);
}
