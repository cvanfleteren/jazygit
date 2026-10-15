package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.process.CommandLine;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Rewrites history through {@code git rebase}, driven without an interactive editor. When a rebase
 * stops half-way, it is aborted again, so the repository is left as it was; a rebase that was already
 * running is left alone.
 */
final class CliRebase {

    static final GitCommand ABORT = git("rebase", "--abort");
    // Merges are recreated instead of flattened, and uncommitted changes are stashed and brought back.
    private static final List<String> REBASE = List.of("rebase", "--autostash", "--keep-empty", "--rebase-merges");
    private static final List<String> INTERACTIVE = REBASE.insert(1, "--interactive");

    private CliRebase() {}

    /**
     * Replaces the message of {@code sha} with an interactive rebase that is told to stop at that commit
     * and which message to use: {@code git rebase --interactive --autostash --keep-empty --rebase-merges <sha>^}, or
     * {@code --root} for a commit without parent. Merges after the commit are recreated, uncommitted changes
     * are stashed and brought back.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> reword(Path workTree, Sha sha, CommitMessage message) {
        // A merge in the todo list is "merge -C <commit>", which has to become "-c" for git to ask for a message.
        String sequenceEditor =
                sequenceEditor(sha, List.of(new Tuple2<>("pick", "reword"), new Tuple2<>("merge -C", "merge -c")));
        return TempFiles.using(
                "jazygit-message",
                file -> Files.writeString(file, message.text()),
                failed -> new GitFailure<GitError>(failed, List.empty()),
                file -> GitFailure.widen(base(workTree, sha.value())
                        .flatMap(base -> rebase(
                                workTree,
                                interactive(
                                                INTERACTIVE.append(base),
                                                sequenceEditor,
                                                CommandLine.format(List.of("cp", file.toString())))
                                        // The message is taken as it was entered: git would strip lines starting with
                                        // "#", like "#123", from a message that comes from an editor.
                                        .withConfig("commit.cleanup", "whitespace")))));
    }

    /**
     * Rebases the checked out branch onto {@code onto}:
     * {@code git rebase --autostash --keep-empty --rebase-merges --no-autosquash <onto>}. Merges are recreated instead of
     * flattened, and fixup commits stay where they are whatever {@code rebase.autoSquash} says.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> onto(Path workTree, Branch.Name onto) {
        return rebase(workTree, git(REBASE.append("--no-autosquash").append(onto.value())));
    }

    /**
     * Folds the commit {@code sha} into its parent with an interactive rebase that is told to turn that
     * commit's {@code pick} into the command of {@code mode}, and to keep the message as git proposes it:
     * {@code git rebase --interactive --autostash --keep-empty --rebase-merges --no-autosquash <parent>^}, or
     * {@code --root} when the parent has no parent. Uncommitted changes are stashed and brought back.
     *
     * @param sha the object id of a commit that has exactly one parent
     * @return the command lines that were run
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> squash(Path workTree, Sha sha, SquashMode mode) {
        return GitProcess.run(workTree, git("rev-parse", sha.value() + "^"))
                .flatMap(parent -> base(workTree, parent.stdout().strip()))
                .flatMap(base -> rebase(
                        workTree,
                        interactive(
                                INTERACTIVE.append("--no-autosquash").append(base),
                                sequenceEditor(sha, List.of(new Tuple2<>("pick", mode.todoCommand()))),
                                "true")));
    }

    /**
     * An interactive rebase whose todo list is edited by {@code sequenceEditor}, and whose messages by
     * {@code editor}. The todo list names every commit by its full object id and spells out its commands,
     * whatever the user's configuration says, so the sequence editor can find the commit it is after.
     */
    private static GitCommand interactive(List<String> args, String sequenceEditor, String editor) {
        return git(args)
                .withConfig("rebase.abbreviateCommands", "false")
                .withConfig("core.abbrev", "no")
                .withEnvironment(HashMap.of("GIT_SEQUENCE_EDITOR", sequenceEditor, "GIT_EDITOR", editor));
    }

    /**
     * A shell function, run by git on the todo list, that turns the line of {@code sha} that starts with
     * the command {@code _1} of one of the {@code replacements} into one with its command {@code _2}. When the
     * todo list has no such line, it fails, so git stops the rebase instead of doing it unchanged.
     */
    private static String sequenceEditor(Sha sha, List<Tuple2<String, String>> replacements) {
        // Plain sed and mv, as "sed -i" differs between GNU and BSD.
        String find =
                replacements.map(r -> "-e '^" + r._1 + " " + sha.value() + " '").mkString(" ");
        String replace = replacements
                .map(r -> "-e 's/^" + r._1 + " " + sha.value() + " /" + r._2 + " " + sha.value() + " /'")
                .mkString(" ");
        return "f() { grep -q " + find + " \"$1\" && sed " + replace
                + " \"$1\" > \"$1.tmp\" && mv \"$1.tmp\" \"$1\"; }; f";
    }

    /**
     * Runs {@code rebase}, and aborts it again when it stopped half-way.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> rebase(Path workTree, GitCommand rebase) {
        return Unfinished.runOrUndo(workTree, rebase, left -> Option.when(left.rebasing(), ABORT));
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
