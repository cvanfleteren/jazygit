package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.Stash;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Lists and creates stashes through the {@code git stash} command line tool.
 */
public final class CliStash {

    // What git prefixes the message of a stash with when it is given one.
    private static final Pattern BRANCH_PREFIX = Pattern.compile("^On [^:]+: ");

    private CliStash() {
    }

    /**
     * Runs {@code git stash list}, newest stash first.
     */
    public static Either<ProcessError, List<Stash>> list(Path workTree) {
        return GitProcess.read(workTree,
                GitCommand.reading("stash", "list", "--format=%gd" + GitOutput.PRETTY_FIELD_SEPARATOR + "%gs"),
                CliStash::parse);
    }

    static Either<UnexpectedOutput, List<Stash>> parse(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(CliStash::stash)).map(Seq::toList);
    }

    private static Either<UnexpectedOutput, Stash> stash(String line) {
        return GitOutput.fields(line, 2)
                .map(fields -> new Stash(fields.get(0), BRANCH_PREFIX.matcher(fields.get(1)).replaceFirst("")));
    }

    /**
     * Runs {@code git stash pop <ref>}. Git refuses e.g. when local changes would be overwritten, or
     * when the stash conflicts, which keeps it.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> pop(Path workTree, String ref) {
        return GitProcess.log(workTree, git("stash", "pop", ref));
    }

    /**
     * Runs {@code git stash drop <ref>}.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> drop(Path workTree, String ref) {
        return GitProcess.log(workTree, git("stash", "drop", ref));
    }

    /**
     * Runs {@code git stash push --include-untracked --message <message>}: stashes the changes, staged and
     * unstaged, and the untracked files. Without a message, git names the stash after the branch and its
     * commit, e.g. {@code WIP on main: abc1234 Add widget}.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(Path workTree, Option<String> message) {
        return GitProcess.log(workTree, git(List.of("stash", "push", "--include-untracked")
                .appendAll(message.toList().flatMap(text -> List.of("--message", text)))));
    }

    /**
     * Whether {@code git stash push} stashed anything, and the commands that found out.
     */
    record Stashed(boolean any, List<ExecutedCommand> commands) {
    }

    /**
     * Runs {@code git stash push --quiet}: stashes the changes to tracked files, to take them elsewhere.
     */
    static Either<GitFailure<ProcessError>, Stashed> pushQuietly(Path workTree) {
        // Git succeeds without stashing when there is nothing to stash; then there is nothing to pop
        // either, and popping would apply an older, unrelated stash. Only the push is logged.
        return count(workTree).flatMap(before ->
                GitProcess.log(workTree, git("stash", "push", "--quiet")).flatMap(pushed ->
                        count(workTree).map(after -> new Stashed(after > before, pushed))));
    }

    /**
     * Runs {@code git stash pop --quiet}: brings back what {@link #pushQuietly} stashed.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> popQuietly(Path workTree) {
        return GitProcess.log(workTree, git("stash", "pop", "--quiet"));
    }

    private static Either<GitFailure<ProcessError>, Integer> count(Path workTree) {
        return GitProcess.run(workTree, git("stash", "list")).map(output -> GitOutput.nonBlankLines(output.stdout()).size());
    }
}
