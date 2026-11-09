package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.process.CommandLine;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Rewrites history through {@code git rebase}, driven without an interactive editor. A rebase that stops on
 * conflicts is left so, for the user to resolve them and go on; one that stops half-way otherwise is aborted again,
 * so the repository is left as it was. A rebase that was already running is left alone.
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
    static Either<GitFailure, List<ExecutedCommand>> reword(Path worktree, Sha sha, CommitMessage message) {
        // A merge in the todo list is "merge -C <commit>", which has to become "-c" for git to ask for a message.
        String sequenceEditor =
                sequenceEditor(sha, List.of(new Tuple2<>("pick", "reword"), new Tuple2<>("merge -C", "merge -c")));
        return TempFiles.using(
                "jazygit-message",
                file -> Files.writeString(file, message.text()),
                failed -> new GitFailure(failed, List.empty()),
                file -> base(worktree, sha.value())
                        .flatMap(base -> rebase(
                                worktree,
                                interactive(
                                                INTERACTIVE.append(base),
                                                sequenceEditor,
                                                CommandLine.format(List.of("cp", file.toString())))
                                        // The message is taken as it was entered: git would strip lines starting with
                                        // "#", like "#123", from a message that comes from an editor.
                                        .withConfig("commit.cleanup", "whitespace"))));
    }

    /**
     * Rebases the checked out branch onto {@code onto}:
     * {@code git rebase --autostash --keep-empty --rebase-merges --no-autosquash refs/heads/<onto>}. Merges are recreated instead of
     * flattened, and fixup commits stay where they are whatever {@code rebase.autoSquash} says.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure, List<ExecutedCommand>> onto(Path worktree, BranchName onto) {
        return rebase(
                worktree, git(REBASE.append("--no-autosquash").append(GitArgs.revision(new BranchRef.Local(onto)))));
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
    static Either<GitFailure, List<ExecutedCommand>> squash(Path worktree, Sha sha, SquashMode mode) {
        return GitProcess.run(worktree, git("rev-parse", sha.value() + "^"))
                .flatMap(parent -> base(worktree, parent.stdout().strip()))
                .flatMap(base -> rebase(
                        worktree,
                        interactive(
                                INTERACTIVE.append("--no-autosquash").append(base),
                                sequenceEditor(sha, List.of(new Tuple2<>("pick", mode.todoCommand()))),
                                "true")));
    }

    /**
     * Folds the last commit into the commit {@code sha} further down, keeping the message of {@code sha}, with an
     * interactive rebase that is told to move the {@code pick} of the last commit to right after that of {@code sha},
     * as a {@code fixup}: {@code git rebase --interactive --autostash --keep-empty --rebase-merges --no-autosquash
     * <sha>^}, or {@code --root} for a commit without parent. Uncommitted changes are stashed and brought back.
     *
     * @param sha the object id of a commit before the last one that is not a merge
     * @return the command lines that were run
     */
    static Either<GitFailure, List<ExecutedCommand>> fixupInto(Path worktree, Sha sha) {
        return GitProcess.run(worktree, git("rev-parse", "HEAD"))
                .map(head -> head.stdout().strip())
                .flatMap(head -> base(worktree, sha.value())
                        .flatMap(base -> rebase(
                                worktree,
                                interactive(
                                        INTERACTIVE.append("--no-autosquash").append(base),
                                        moveUnder(sha, head),
                                        "true"))));
    }

    /**
     * Removes the commit {@code sha} with an interactive rebase that is told to turn that commit's {@code pick}
     * into {@code drop}: {@code git rebase --interactive --autostash --keep-empty --rebase-merges --no-autosquash
     * <sha>^}, or {@code --root} for a commit without parent. Uncommitted changes are stashed and brought back.
     *
     * @param sha the object id of a commit that is not a merge
     * @return the command lines that were run
     */
    static Either<GitFailure, List<ExecutedCommand>> drop(Path worktree, Sha sha) {
        return base(worktree, sha.value())
                .flatMap(base -> rebase(
                        worktree,
                        interactive(
                                INTERACTIVE.append("--no-autosquash").append(base),
                                sequenceEditor(sha, List.of(new Tuple2<>("pick", "drop"))),
                                "true")));
    }

    /**
     * Rebases a stack onto {@code base} in one interactive rebase, whose todo jazygit writes instead of an editor
     * (ADR 74): for each of {@code layers} from the bottom up, {@code pick} its own commits, then
     * {@code update-ref refs/heads/<branch>}; none for the top, the last of them, which the rebase checks out and moves
     * itself. {@code git rebase --interactive --keep-empty --no-autosquash --empty=drop --onto <base> <base> <top>}: a
     * commit that has nothing left once picked, as the base has it already, is dropped. Without {@code --autostash}:
     * git refuses changes that are not committed, which would else come back on the top instead of where they were.
     * It does not start when a branch moved since its commits were read, which the todo would leave out, or is now
     * checked out in another worktree, under whose files an {@code update-ref} would move it.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure, List<ExecutedCommand>> stack(
            Path worktree, Revision base, List<StackLayer.Commits> layers) {
        BranchName top = layers.last().branch();
        return changedSince(worktree, layers)
                .flatMap(changed -> changed.isDefined()
                        ? Either.left(new GitFailure(changed.get(), List.empty()))
                        : TempFiles.using(
                                "jazygit-stack-todo",
                                file -> Files.writeString(file, todo(layers)),
                                failed -> new GitFailure(failed, List.empty()),
                                file -> rebase(
                                        worktree,
                                        interactive(
                                                        List.of(
                                                                "rebase",
                                                                "--interactive",
                                                                "--keep-empty",
                                                                "--no-autosquash",
                                                                "--empty=drop",
                                                                "--onto",
                                                                GitArgs.revision(base),
                                                                GitArgs.revision(base),
                                                                // Its name, not its ref: given refs/heads/<top>, git
                                                                // would rebase a detached HEAD and leave the branch
                                                                // where it was.
                                                                GitArgs.name(top)),
                                                        CommandLine.format(List.of("cp", file.toString())),
                                                        "true")
                                                // The todo leaves out on purpose what the base or a merged branch
                                                // below has already, which git would else stop for.
                                                .withConfig("rebase.missingCommitsCheck", "ignore"))));
    }

    /**
     * Why a rebase of {@code layers} does not start, if it does not: one of their branches is no longer where its
     * commits were read, or is checked out in another worktree. One checked out here is not: the rebase checks out
     * the top first.
     */
    private static Either<GitFailure, Option<GitError>> changedSince(Path worktree, List<StackLayer.Commits> layers) {
        String format =
                List.of("%(refname)", "%(objectname)", "%(worktreepath)").mkString(GitOutput.REF_FIELD_SEPARATOR);
        return GitProcess.ask(worktree, GitCommand.reading("symbolic-ref", "--quiet", "HEAD"))
                .map(head -> head.map(found -> found.stdout().strip()))
                .flatMap(head -> GitProcess.run(
                                worktree,
                                GitCommand.reading(List.of("for-each-ref", "--format=" + format)
                                        .appendAll(layers.map(
                                                layer -> GitArgs.revision(new BranchRef.Local(layer.branch()))))))
                        .flatMap(refs -> Either.sequenceRight(
                                        GitOutput.nonBlankLines(refs.stdout()).map(line -> GitOutput.fields(line, 3)))
                                .mapLeft(error -> new GitFailure(error, refs.log()))
                                .map(read -> List.ofAll(read).toMap(fields -> fields.get(0), fields -> fields.tail()))
                                .map(read -> layers.iterator()
                                        .flatMap(layer -> changed(layer, read, head))
                                        .headOption())));
    }

    /**
     * Why the branch of {@code layer} stops a rebase of the stack, if it does, as {@code refs} has the branches,
     * their tips and worktrees by their refs, while {@code head} is checked out here.
     */
    private static Option<GitError> changed(
            StackLayer.Commits layer, Map<String, List<String>> refs, Option<String> head) {
        String ref = GitArgs.revision(new BranchRef.Local(layer.branch()));
        Option<List<String>> found = refs.get(ref);
        if (found.forAll(
                tipAndWorktree -> !tipAndWorktree.get(0).equals(layer.tip().value()))) {
            return Option.of(new GitError.StackBranchMoved(layer.branch()));
        }
        String elsewhere = found.get().get(1);
        return Option.when(
                !elsewhere.isBlank() && !head.contains(ref),
                new GitError.StackBranchCheckedOutElsewhere(layer.branch(), elsewhere));
    }

    /**
     * The todo of a rebase of {@code layers}: the own commits of each, then where its branch goes, but for the top.
     * A todo without a commit to pick says {@code noop}, which git otherwise takes for having nothing to do.
     */
    static String todo(List<StackLayer.Commits> layers) {
        List<String> lines = layers.zipWithIndex()
                .flatMap(layer -> layer._1()
                        .commits()
                        .map(sha -> "pick " + sha.value())
                        .appendAll(
                                layer._2() < layers.size() - 1
                                        ? List.of("update-ref "
                                                + GitArgs.revision(new BranchRef.Local(
                                                        layer._1().branch())))
                                        : List.empty()));
        return (layers.exists(layer -> !layer.commits().isEmpty()) ? lines : lines.prepend("noop")).mkString("\n")
                + "\n";
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
     * A shell function, run by git on the todo list, that moves the line picking {@code fixup} to right after the
     * one picking {@code sha}, as a {@code fixup}. When the todo list lacks either line, it fails, so git stops the
     * rebase instead of doing it unchanged.
     */
    private static String moveUnder(Sha sha, String fixup) {
        String target = "pick " + sha.value() + " ";
        String moved = "pick " + fixup + " ";
        // awk, as inserting a line with sed differs between GNU and BSD.
        return "f() { grep -q '^" + target + "' \"$1\" && grep -q '^" + moved + "' \"$1\""
                + " && awk -v t='" + target + "' -v m='" + moved + "' -v l='fixup " + fixup + "'"
                + " 'index($0, m) == 1 { next } { print } index($0, t) == 1 { print l }'"
                + " \"$1\" > \"$1.tmp\" && mv \"$1.tmp\" \"$1\"; }; f";
    }

    /**
     * Runs {@code rebase}, and aborts it again when it stopped half-way, other than on conflicts.
     */
    private static Either<GitFailure, List<ExecutedCommand>> rebase(Path worktree, GitCommand rebase) {
        return Unfinished.runOrUndo(worktree, rebase, left -> Option.when(left.rebasing(), ABORT));
    }

    /**
     * What to rebase onto to rewrite {@code commit}: its parent, or {@code --root} for a root commit.
     */
    private static Either<GitFailure, String> base(Path worktree, String commit) {
        // "<sha> <parents...>": only the sha when it is a root commit.
        return GitProcess.run(worktree, git("rev-list", "--parents", "-n", "1", commit))
                .map(output -> output.stdout().strip().contains(" ") ? commit + "^" : "--root");
    }
}
