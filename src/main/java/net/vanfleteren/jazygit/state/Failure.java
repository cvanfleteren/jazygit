package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.ExecutedCommand;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;

/**
 * Why a {@link Cmd} failed, as data: git refused, or there is a bug.
 */
public sealed interface Failure {

    /**
     * The command lines that were executed before it failed, the failing one last; they belong in the
     * command log.
     */
    List<ExecutedCommand> commands();

    /**
     * Git failed.
     *
     * @param cleanupFailed why cleaning up after the failure failed too, if it did
     */
    record Git(GitError error, List<ExecutedCommand> commands, Option<ProcessError> cleanupFailed) implements Failure {

        public Git(GitError error) {
            this(error, List.empty(), Option.none());
        }
    }

    /**
     * The command threw: a bug, which is reported rather than taking the app down.
     */
    record Bug(Throwable cause) implements Failure {

        @Override
        public List<ExecutedCommand> commands() {
            return List.empty();
        }
    }

    static Failure of(GitFailure<?> failure) {
        return new Git(failure.error(), failure.commands(), failure.cleanupFailed());
    }
}
