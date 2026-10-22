package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Lists and creates stashes through the {@code git stash} command line tool.
 */
final class CliStash {

    // What git says when the staged changes of a stash do not apply.
    private static final String WITHOUT_INDEX = "Try without --index";
    // What git prefixes the message of a stash with when it is given one.
    private static final Pattern BRANCH_PREFIX = Pattern.compile("^On [^:]+: ");
    // The branch a stash was made on, in the message git gives it with a message and without one.
    private static final Pattern STASHED_ON = Pattern.compile("^(?:WIP on|On) ([^:]+): ");

    private CliStash() {}

    /**
     * The stashes, newest first, as {@code git stash list} lists them: it runs this {@code git log} of the reflog of
     * the stash itself, so running it directly takes one process instead of two. That the stashes are the reflog of
     * {@code refs/stash} is how git's documentation of {@code git stash} describes them, not a detail of how it is
     * built. Without a stash, there is no reflog, which {@code --ignore-missing} makes an empty list rather than an
     * error.
     */
    static Either<ProcessError, List<Stash>> list(Path workTree) {
        return GitProcess.read(
                workTree,
                GitCommand.reading(
                        "log",
                        "--walk-reflogs",
                        "--first-parent",
                        "--ignore-missing",
                        "--format=%gd" + GitOutput.PRETTY_FIELD_SEPARATOR + "%H" + GitOutput.PRETTY_FIELD_SEPARATOR
                                + "%gs" + GitOutput.PRETTY_FIELD_SEPARATOR + "%at",
                        "refs/stash",
                        "--"),
                CliStash::parse);
    }

    static Either<UnexpectedOutput, List<Stash>> parse(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(CliStash::stash))
                .map(Seq::toList);
    }

    private static Either<UnexpectedOutput, Stash> stash(String line) {
        return GitOutput.fields(line, 4)
                .flatMap(fields -> GitOutput.epochSeconds(fields.get(3))
                        .map(time -> new Stash(
                                fields.get(0),
                                new Sha(fields.get(1)),
                                BRANCH_PREFIX.matcher(fields.get(2)).replaceFirst(""),
                                branch(fields.get(2)),
                                time)));
    }

    private static Option<String> branch(String message) {
        Matcher matcher = STASHED_ON.matcher(message);
        return matcher.find() ? Option.of(matcher.group(1)) : Option.none();
    }

    /**
     * What {@code stash} stashed, as {@code git stash show} shows it: the changed files with their change counts, a
     * summary line, a blank line and the diff, read with {@code options}. Its untracked files are in it, whatever
     * the user's {@code stash.show*} settings say. Long output is cut off.
     */
    static Either<ProcessError, DiffText> changes(Path workTree, Sha stash, DiffOptions options) {
        return GitProcess.read(
                workTree,
                GitCommand.reading(List.of(
                                "stash",
                                "show",
                                "--include-untracked",
                                "--stat=1000",
                                "--patch",
                                "--no-color",
                                "--no-ext-diff")
                        .appendAll(CliDiff.flags(options))
                        .append(stash.value())),
                output -> Either.right(CliDiff.truncated(output)));
    }

    /**
     * Runs {@code git stash pop <ref>} for {@code stash}, under the name it has now. Git refuses e.g. when local
     * changes would be overwritten, or when the stash conflicts, which keeps it.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> pop(Path workTree, Stash stash) {
        return onStash(workTree, stash, ref -> git("stash", "pop", ref));
    }

    /**
     * Runs {@code git stash drop <ref>} for {@code stash}, under the name it has now.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Path workTree, Stash stash) {
        return onStash(workTree, stash, ref -> git("stash", "drop", ref));
    }

    /**
     * Runs the {@code command} of the name {@code stash} has now: a stash added or removed since it was listed
     * renamed it, and its old name is another stash now. A stash that is gone is a {@link StashGone}.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> onStash(
            Path workTree, Stash stash, Function<String, GitCommand> command) {
        return list(workTree)
                .<GitFailure<GitError>>mapLeft(error -> new GitFailure<>(error, List.empty()))
                .flatMap(stashes -> stashes.find(listed -> listed.sha().equals(stash.sha()))
                        .map(current -> GitFailure.<GitError, List<ExecutedCommand>>widen(
                                GitProcess.log(workTree, command.apply(current.ref()))))
                        .getOrElse(() -> Either.left(new GitFailure<>(new StashGone(stash.message()), List.empty()))));
    }

    /**
     * Runs {@code git stash push --include-untracked --message <message>}: stashes the changes, staged and
     * unstaged, and the untracked files. Without a message, git names the stash after the branch and its
     * commit, e.g. {@code WIP on main: abc1234 Add widget}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(Path workTree, Option<String> message) {
        return GitProcess.log(
                workTree,
                git(List.of("stash", "push", "--include-untracked")
                        .appendAll(message.toList().flatMap(text -> List.of("--message", text)))));
    }

    /**
     * Runs {@code git stash push --quiet}: stashes the changes to tracked files, to take them elsewhere.
     */
    static Either<GitFailure<ProcessError>, Stashed> pushQuietly(Path workTree) {
        // Git succeeds without stashing when there is nothing to stash; then there is nothing to pop
        // either, and popping would apply an older, unrelated stash. Only the push is logged.
        return count(workTree)
                .flatMap(before -> GitProcess.log(workTree, git("stash", "push", "--quiet"))
                        .flatMap(pushed -> count(workTree).map(after -> new Stashed(after > before, pushed))));
    }

    /**
     * Runs {@code git stash pop --index --quiet}: brings back what {@link #pushQuietly} stashed, staged changes
     * staged again. When the staged changes do not apply where they are popped, git pops nothing, and they are
     * popped unstaged instead, with {@code git stash pop --quiet}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> popQuietly(Path workTree) {
        return GitProcess.log(workTree, git("stash", "pop", "--index", "--quiet"))
                .fold(
                        failure -> failure.error() instanceof CommandFailed failed && failed.says(WITHOUT_INDEX)
                                ? GitProcess.log(workTree, git("stash", "pop", "--quiet"))
                                        .map(failure.commands()::appendAll)
                                        .mapLeft(again -> again.after(failure.commands()))
                                : Either.left(failure),
                        Either::right);
    }

    /**
     * How many stashes there are. Only asked, so not logged.
     */
    private static Either<GitFailure<ProcessError>, Integer> count(Path workTree) {
        return list(workTree).map(List::size).mapLeft(error -> new GitFailure<>(error, List.empty()));
    }

    /**
     * Whether {@code git stash push} stashed anything, and the commands that found out.
     */
    record Stashed(boolean any, List<ExecutedCommand> commands) {}
}
