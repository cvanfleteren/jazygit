package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Stash;

import java.nio.file.Path;
import java.util.regex.Pattern;

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
     *
     * @throws IllegalStateException if git fails; the message then is git's own explanation
     */
    public static List<Stash> list(Path workTree) {
        String output = CliCheckout.run(workTree, "git", "--no-optional-locks", "stash", "list",
                "--format=%gd" + SEPARATOR + "%gs");
        return List.ofAll(output.lines()).filter(line -> !line.isBlank()).map(CliStash::parse);
    }

    static Stash parse(String line) {
        int at = line.indexOf(SEPARATOR);
        return new Stash(line.substring(0, at), BRANCH_PREFIX.matcher(line.substring(at + 1)).replaceFirst(""));
    }

    /**
     * Runs {@code git stash pop <ref>}.
     *
     * @return the command lines that were run
     * @throws GitCommandException if git refuses, e.g. because local changes would be overwritten, or the
     *                             stash conflicts and is kept; the message then is git's own explanation
     */
    public static List<String> pop(Path workTree, String ref) {
        return List.of(CliCheckout.exec(workTree, "git", "stash", "pop", ref).command());
    }

    /**
     * Runs {@code git stash drop <ref>}.
     *
     * @return the command lines that were run
     * @throws GitCommandException if git refuses; the message then is git's own explanation
     */
    public static List<String> drop(Path workTree, String ref) {
        return List.of(CliCheckout.exec(workTree, "git", "stash", "drop", ref).command());
    }

    /**
     * Runs {@code git stash push --include-untracked --message <message>}: stashes the changes, staged and
     * unstaged, and the untracked files.
     *
     * @return the command lines that were run
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static List<String> push(Path workTree, String message) {
        return List.of(CliCheckout.exec(workTree, "git", "stash", "push", "--include-untracked", "--message",
                message).command());
    }
}
