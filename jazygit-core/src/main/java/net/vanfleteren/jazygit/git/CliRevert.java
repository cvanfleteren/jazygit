package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.StoppedOnConflicts;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.process.CommandLine;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Undoes commits with a new commit through {@code git revert}. A revert that stops on conflicts is left so, for the
 * user to resolve them; one that fails otherwise is undone again, so the repository is left as it was. Git itself
 * refuses to start one while uncommitted changes would be overwritten, or while the repository is in the middle
 * of something else, which is then left alone.
 */
final class CliRevert {

    private static final GitCommand ABORT = git("revert", "--abort");

    private CliRevert() {}

    /**
     * {@code git revert --edit [-m 1] <sha>}, with an editor that writes {@code message}: commits the inverse of
     * {@code commit} on top of the checked out branch; a merge is reverted against its first parent. When it stops
     * on conflicts, git leaves its own message for continuing; it is replaced by {@code message}.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> revert(
            Path workTree, Commit commit, CommitMessage message) {
        List<String> mainline = commit.isMerge() ? List.of("-m", "1") : List.of();
        return TempFiles.using(
                "jazygit-message",
                file -> Files.writeString(file, message.text()),
                failed -> new GitFailure<GitError>(failed, List.empty()),
                file -> Unfinished.runOrUndo(
                                workTree,
                                git(List.of("revert", "--edit")
                                                .appendAll(mainline)
                                                .append(commit.sha().value()))
                                        .withEnvironment(HashMap.of(
                                                "GIT_EDITOR", CommandLine.format(List.of("cp", file.toString()))))
                                        // The message is taken as it was entered: git would strip lines starting
                                        // with "#", like "#123", from a message that comes from an editor.
                                        .withConfig("commit.cleanup", "whitespace"),
                                left -> Option.when(left.picking(), ABORT))
                        .peekLeft(failure -> {
                            if (failure.error() instanceof StoppedOnConflicts) {
                                keepMessage(workTree, message);
                            }
                        }));
    }

    /**
     * Makes {@code message} the one continuing the stopped revert commits. Without it, that is git's own message, so
     * failing to write it loses no more than what was typed; the conflicts are what matters.
     */
    private static void keepMessage(Path workTree, CommitMessage message) {
        GitProcess.run(workTree, git("rev-parse", "--git-path", "MERGE_MSG"))
                .forEach(output -> Try.run(
                        () -> Files.writeString(workTree.resolve(output.stdout().strip()), message.text())));
    }
}
