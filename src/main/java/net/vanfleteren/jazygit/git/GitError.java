package net.vanfleteren.jazygit.git;

import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchRef;

/**
 * Why a git operation failed, as data. Turning it into text is up to the caller.
 *
 * <p>Which failures an operation can have is documented on the operation itself.
 */
public sealed interface GitError {

    /**
     * A failure that running any git command can have.
     */
    sealed interface ProcessError extends GitError {}

    /**
     * The {@code git} executable could not be started.
     *
     * @param cause why the process could not be started
     */
    record GitUnavailable(IOException cause) implements ProcessError {}

    /**
     * @param operation the git subcommand, e.g. {@code checkout}
     */
    record TimedOut(String operation, Path workTree) implements ProcessError {}

    /**
     * @param operation the git subcommand, e.g. {@code checkout}
     */
    record Interrupted(String operation) implements ProcessError {}

    /**
     * Git ran, but refused.
     *
     * @param explanation the line git printed about why, preferring its error output; none if it printed
     *                    nothing
     * @param output      everything git printed on its error output
     */
    record CommandFailed(Option<String> explanation, int exitCode, String output) implements ProcessError {

        public CommandFailed(String explanation, int exitCode) {
            this(Option.of(explanation).filter(text -> !text.isEmpty()), exitCode, explanation);
        }

        /**
         * Whether git refused without saying anything, with exit code 1. That is how commands that answer
         * a question say "no", e.g. {@code git diff --quiet} when there are differences.
         */
        public boolean silent() {
            return exitCode == 1 && explanation.isEmpty();
        }

        /**
         * Whether git printed {@code text}, in its explanation or elsewhere on its error output.
         */
        public boolean says(String text) {
            return explanation.exists(line -> line.contains(text)) || output.contains(text);
        }
    }

    /**
     * Git printed something that could not be made sense of.
     *
     * @param output the part of the output that could not be parsed
     */
    record UnexpectedOutput(String output) implements ProcessError {}

    /**
     * No git repository contains the directory.
     */
    record NotARepository(Path directory) implements GitError {}

    /**
     * The repository is bare: there are no files to show.
     *
     * @param directory the directory the repository was looked for from
     */
    record NoWorkTree(Path directory) implements GitError {}

    /**
     * There is no such branch, local or remote-tracking.
     */
    record UnknownBranch(BranchRef branch) implements GitError {}

    /**
     * Git refused a checkout because uncommitted changes would be overwritten. Stashing them first lets
     * it go through.
     *
     * @param explanation git's explanation
     */
    record LocalChanges(String explanation) implements GitError {}

    /**
     * The branch was checked out, but the stashed changes could not be applied on it, so they stay in
     * the stash.
     */
    record StashNotRestored(ProcessError cause) implements GitError {}

    /**
     * The stash is not there anymore: it was popped or dropped since it was listed.
     *
     * @param message what it was stashed as
     */
    record StashGone(String message) implements GitError {}

    /**
     * A temporary file git needed could not be written.
     */
    record TempFileFailed(Throwable cause) implements GitError {}

    /**
     * An untracked path that was not deleted because it is not inside the working tree.
     */
    record RefusedPath(String path) implements GitError {}
}
