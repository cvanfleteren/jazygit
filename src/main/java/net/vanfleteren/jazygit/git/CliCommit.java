package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Creates and rewrites commits through {@code git commit}, and through {@link CliRebase} for commits before
 * the last one.
 */
final class CliCommit {

    private CliCommit() {}

    /**
     * {@code git commit -m <summary> [-m <description>]}: commits the index.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(Path workTree, CommitMessage message) {
        return run(workTree, List.of("commit", "--quiet").appendAll(messageArgs(message)));
    }

    /**
     * {@code git commit --amend --no-edit}: amends the last commit with the index.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> amend(Path workTree) {
        return run(workTree, List.of("commit", "--quiet", "--amend", "--no-edit"));
    }

    /**
     * Replaces the message of {@code sha}: by amending when it is HEAD, else by {@link CliRebase#reword}.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<RewordError>, List<ExecutedCommand>> reword(
            Path workTree, Sha sha, CommitMessage message) {
        return GitFailure.<RewordError, Option<Sha>>widen(CliLog.resolve(workTree, "HEAD"))
                .flatMap(head -> head.contains(sha)
                        ? GitFailure.widen(rewordHead(workTree, message))
                        : CliRebase.reword(workTree, sha, message));
    }

    /**
     * Folds the commit {@code sha} into its parent, as {@link CliRebase#squash} does.
     *
     * @param sha the object id of a commit that has exactly one parent
     * @return the command lines that were run
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> squash(Path workTree, Sha sha, SquashMode mode) {
        return CliRebase.squash(workTree, sha, mode);
    }

    /**
     * {@code git commit --allow-empty --amend --only -m <summary> [-m <description>]}: replaces the
     * message of the last commit, whatever is staged.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> rewordHead(
            Path workTree, CommitMessage message) {
        return run(
                workTree,
                List.of("commit", "--quiet", "--allow-empty", "--amend", "--only")
                        .appendAll(messageArgs(message)));
    }

    private static List<String> messageArgs(CommitMessage message) {
        return message.hasDescription()
                ? List.of("-m", message.summary(), "-m", message.description())
                : List.of("-m", message.summary());
    }

    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> run(Path workTree, List<String> args) {
        return GitProcess.log(workTree, git(args));
    }
}
