package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitProcess.Output;
import net.vanfleteren.jazygit.git.model.Stash;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Lists and creates stashes through the {@code git stash} command line tool.
 */
public final class CliStash {

    private static final char SEPARATOR = '\u001f';
    // What git prefixes the message of a stash with when it is given one.
    private static final Pattern BRANCH_PREFIX = Pattern.compile("^On [^:]+: ");

    private CliStash() {
    }

    /**
     * Runs {@code git stash list}, newest stash first.
     */
    public static Either<ProcessError, List<Stash>> list(Path workTree) {
        return GitProcess.run(workTree, git("--no-optional-locks", "stash", "list", "--format=%gd" + SEPARATOR + "%gs"))
                .map(output -> List.ofAll(output.stdout().lines()).filter(line -> !line.isBlank()).map(CliStash::parse))
                .mapLeft(GitFailure::error);
    }

    static Stash parse(String line) {
        int at = line.indexOf(SEPARATOR);
        return new Stash(line.substring(0, at), BRANCH_PREFIX.matcher(line.substring(at + 1)).replaceFirst(""));
    }

    /**
     * Runs {@code git stash pop <ref>}. Git refuses e.g. when local changes would be overwritten, or
     * when the stash conflicts, which keeps it.
     */
    public static Either<GitFailure<ProcessError>, List<String>> pop(Path workTree, String ref) {
        return GitProcess.run(workTree, git("stash", "pop", ref)).map(Output::log);
    }

    /**
     * Runs {@code git stash drop <ref>}.
     */
    public static Either<GitFailure<ProcessError>, List<String>> drop(Path workTree, String ref) {
        return GitProcess.run(workTree, git("stash", "drop", ref)).map(Output::log);
    }

    /**
     * Runs {@code git stash push --include-untracked --message <message>}: stashes the changes, staged and
     * unstaged, and the untracked files.
     */
    public static Either<GitFailure<ProcessError>, List<String>> push(Path workTree, String message) {
        return GitProcess.run(workTree, git("stash", "push", "--include-untracked", "--message", message))
                .map(Output::log);
    }
}
