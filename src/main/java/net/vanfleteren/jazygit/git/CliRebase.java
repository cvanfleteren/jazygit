package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.CommandLine;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Rewrites history through {@code git rebase}, driven without an interactive editor. When a rebase
 * fails, it is aborted again, so the repository is left as it was.
 */
public final class CliRebase {

    private static final GitCommand ABORT = git("rebase", "--abort");
    // Merges are recreated instead of flattened, and uncommitted changes are stashed and brought back.
    private static final List<String> REBASE = List.of("rebase", "--autostash", "--keep-empty", "--rebase-merges");
    private static final List<String> INTERACTIVE = REBASE.insert(1, "--interactive");

    private CliRebase() {
    }

    /**
     * Replaces the message of {@code sha}: by amending when it is HEAD, else by {@link #reword}.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<RewordError>, List<ExecutedCommand>> rewordAny(Path workTree, Sha sha,
                                                                        CommitMessage message) {
        return GitFailure.<RewordError, Option<Sha>>widen(CliLog.resolve(workTree, "HEAD"))
                .flatMap(head -> head.contains(sha)
                        ? GitFailure.widen(CliIndex.reword(workTree, message))
                        : reword(workTree, sha, message));
    }

    /**
     * Replaces the message of {@code sha} with an interactive rebase that is told to stop at that commit
     * and which message to use: {@code git rebase --interactive --autostash --keep-empty --rebase-merges <sha>^}, or
     * {@code --root} for a commit without parent. Merges after the commit are recreated, uncommitted changes
     * are stashed and brought back.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<RewordError>, List<ExecutedCommand>> reword(Path workTree, Sha sha, CommitMessage message) {
        // A merge in the todo list is "merge -C <commit>", which has to become "-c" for git to ask for a message.
        String sequenceEditor = sequenceEditor(sha, "pick", "reword")
                + " -e 's/^merge -C " + sha.shortSha() + "/merge -c " + sha.shortSha() + "/'";
        return TempFiles.using("jazygit-message", file -> Files.writeString(file, message.text()),
                failed -> new GitFailure<RewordError>(failed, List.empty()),
                file -> GitFailure.<RewordError, List<ExecutedCommand>>widen(base(workTree, sha.value()).flatMap(base ->
                        rebase(workTree, git(INTERACTIVE.append(base)).withEnvironment(HashMap.of(
                                "GIT_SEQUENCE_EDITOR", sequenceEditor,
                                "GIT_EDITOR", CommandLine.format(List.of("cp", file.toString()))))))));
    }

    /**
     * Rebases the checked out branch onto {@code onto}:
     * {@code git rebase --autostash --keep-empty --rebase-merges --no-autosquash <onto>}. Merges are recreated instead of
     * flattened, and fixup commits stay where they are whatever {@code rebase.autoSquash} says.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> onto(Path workTree, String onto) {
        return rebase(workTree, git(REBASE.append("--no-autosquash").append(onto)));
    }

    /**
     * Squashes the commit {@code sha} into its parent with an interactive rebase that is told to turn that
     * commit's {@code pick} into {@code squash} and to keep the combined message as git proposes it:
     * {@code git rebase --interactive --autostash --keep-empty --rebase-merges --no-autosquash <parent>^}, or
     * {@code --root} when the parent has no parent. Uncommitted changes are stashed and brought back.
     *
     * @param sha the object id of a commit that has exactly one parent
     * @return the command lines that were run
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> squash(Path workTree, Sha sha) {
        return GitProcess.run(workTree, git("rev-parse", sha.value() + "^"))
                .flatMap(parent -> base(workTree, parent.stdout().strip()))
                .flatMap(base -> rebase(workTree, git(INTERACTIVE.append("--no-autosquash").append(base))
                        .withEnvironment(HashMap.of(
                                "GIT_SEQUENCE_EDITOR", sequenceEditor(sha, "pick", "squash"),
                                "GIT_EDITOR", "true"))));
    }

    /**
     * A {@code sed} command that turns the {@code from} line of {@code sha} in the todo list into a
     * {@code to} line. The todo list names the commit by an abbreviation of at least 7 characters.
     */
    private static String sequenceEditor(Sha sha, String from, String to) {
        return "sed -i -e 's/^" + from + " " + sha.shortSha() + "/" + to + " " + sha.shortSha() + "/'";
    }

    /**
     * Runs {@code rebase}, and aborts it again when it fails.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> rebase(Path workTree, GitCommand rebase) {
        return GitProcess.runOrUndo(workTree, rebase, ABORT, () -> true);
    }

    /**
     * What to rebase onto to rewrite {@code commit}: its parent, or {@code --root} for a root commit.
     */
    private static Either<GitFailure<ProcessError>, String> base(Path workTree, String commit) {
        // "<sha> <parents...>": only the sha when it is a root commit.
        return GitProcess.run(workTree, git("rev-list", "--parents", "-n", "1", commit))
                .map(output -> output.stdout().strip().contains(" ") ? commit + "^" : "--root");
    }
}
