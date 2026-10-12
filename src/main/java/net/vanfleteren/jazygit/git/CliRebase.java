package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.GitProcess.Output;
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

    private CliRebase() {
    }

    /**
     * Replaces the message of {@code sha} with an interactive rebase that is told to stop at that commit
     * and which message to use: {@code git rebase --interactive --autostash --keep-empty --rebase-merges <sha>^}, or
     * {@code --root} for a commit without parent. Merges after the commit are recreated, uncommitted changes
     * are stashed and brought back.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<RewordError>, List<String>> reword(Path workTree, Sha sha, CommitMessage message) {
        String abbreviated = sha.shortSha();
        // The todo list names the commit by an abbreviation of at least 7 characters. A merge in it
        // is "merge -C <commit>", which has to become "-c" for git to ask for a message.
        String sequenceEditor = "sed -i -e 's/^pick " + abbreviated + "/reword " + abbreviated + "/'"
                + " -e 's/^merge -C " + abbreviated + "/merge -c " + abbreviated + "/'";
        return TempFiles.using("jazygit-message", file -> Files.writeString(file, message.text()),
                        file -> base(workTree, sha.value()).flatMap(base -> rebase(workTree,
                                git("rebase", "--interactive", "--autostash", "--keep-empty", "--rebase-merges", base)
                                        .withEnvironment(HashMap.of(
                                                "GIT_SEQUENCE_EDITOR", sequenceEditor,
                                                "GIT_EDITOR", "cp '" + file + "'")))))
                .fold(failed -> Either.left(new GitFailure<>(failed, List.empty())), GitFailure::widen);
    }

    /**
     * Rebases the checked out branch onto {@code onto}:
     * {@code git rebase --autostash --keep-empty --rebase-merges --no-autosquash <onto>}. Merges are recreated instead of
     * flattened, and fixup commits stay where they are whatever {@code rebase.autoSquash} says.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<ProcessError>, List<String>> onto(Path workTree, String onto) {
        return rebase(workTree, git("rebase", "--autostash", "--keep-empty", "--rebase-merges", "--no-autosquash", onto));
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
    public static Either<GitFailure<ProcessError>, List<String>> squash(Path workTree, Sha sha) {
        String abbreviated = sha.shortSha();
        return GitProcess.run(workTree, git("rev-parse", sha + "^"))
                .flatMap(parent -> base(workTree, parent.stdout().strip()))
                .flatMap(base -> rebase(workTree,
                        git("rebase", "--interactive", "--autostash", "--keep-empty", "--rebase-merges",
                                "--no-autosquash", base)
                                .withEnvironment(HashMap.of(
                                        "GIT_SEQUENCE_EDITOR", "sed -i -e 's/^pick " + abbreviated + "/squash " + abbreviated + "/'",
                                        "GIT_EDITOR", "true"))));
    }

    /**
     * Runs {@code rebase}, and aborts it again when it fails.
     */
    private static Either<GitFailure<ProcessError>, List<String>> rebase(Path workTree, GitCommand rebase) {
        return GitProcess.run(workTree, rebase)
                .map(Output::log)
                .mapLeft(failure -> failure.followedBy(GitProcess.attempt(workTree, ABORT)));
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
