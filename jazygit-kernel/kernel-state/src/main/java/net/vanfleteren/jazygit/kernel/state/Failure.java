package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.settings.SettingsProblem;

/**
 * Why a {@link Cmd} failed, as data: git or a feature refused, the settings could not be kept, or there is a bug.
 */
public sealed interface Failure {

    static Failure of(GitFailure failure) {
        return new Git(failure.error(), failure.commands(), failure.cleanupFailed());
    }

    /**
     * Git refused before any command ran.
     */
    static Failure of(GitError error) {
        return new Git(error);
    }

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
    record Git(GitError error, List<ExecutedCommand> commands, Option<GitError> cleanupFailed) implements Failure {

        public Git(GitError error) {
            this(error, List.empty(), Option.none());
        }
    }

    /**
     * A failure that says itself, as a feature's does: the kernel names none of them (ADR 171), as
     * {@link net.vanfleteren.jazygit.kernel.state.operation.Notice.Phrased} says a notice (ADR 169).
     */
    non-sealed interface Phrased extends Failure {

        /**
         * What it says to the user.
         */
        Phrase phrase();
    }

    /**
     * The settings could not be kept.
     */
    record Settings(SettingsProblem problem) implements Failure {

        @Override
        public List<ExecutedCommand> commands() {
            return List.empty();
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
}
