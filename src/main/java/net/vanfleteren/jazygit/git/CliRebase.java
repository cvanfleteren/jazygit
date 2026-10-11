package net.vanfleteren.jazygit.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rewrites history through {@code git rebase}, driven without an interactive editor.
 */
public final class CliRebase {

    private CliRebase() {
    }

    /**
     * Replaces the message of {@code sha} with an interactive rebase that is told to stop at that commit
     * and which message to use: {@code git rebase --interactive --autostash --keep-empty --rebase-merges <sha>^}, or
     * {@code --root} for a commit without parent. Merges after the commit are recreated, uncommitted changes
     * are stashed and brought back. When the rebase fails, it is aborted again, so the repository is left as
     * it was.
     *
     * @return the command lines that were run
     * @throws GitCommandException if git refuses, with git's explanation as message
     */
    public static List<String> reword(Path workTree, String sha, String summary, String description) {
        Path message = writeMessage(summary, description);
        try {
            String abbreviated = sha.substring(0, Math.min(7, sha.length()));
            // The todo list names the commit by an abbreviation of at least 7 characters. A merge in it
            // is "merge -C <commit>", which has to become "-c" for git to ask for a message.
            String sequenceEditor = "sed -i -e 's/^pick " + abbreviated + "/reword " + abbreviated + "/'"
                    + " -e 's/^merge -C " + abbreviated + "/merge -c " + abbreviated + "/'";
            Map<String, String> environment = Map.of(
                    "GIT_SEQUENCE_EDITOR", sequenceEditor,
                    "GIT_EDITOR", "cp '" + message + "'");
            String base = hasParent(workTree, sha) ? sha + "^" : "--root";
            List<String> executed = new ArrayList<>();
            try {
                executed.add(CliCheckout.exec(workTree, environment, "git", "rebase", "--interactive",
                        "--autostash", "--keep-empty", "--rebase-merges", base).command());
            } catch (GitCommandException e) {
                executed.addAll(e.commands());
                try {
                    executed.add(CliCheckout.exec(workTree, "git", "rebase", "--abort").command());
                } catch (GitCommandException abortFailure) {
                    executed.addAll(abortFailure.commands());
                }
                throw new GitCommandException(e.getMessage(), executed, e);
            }
            return executed;
        } finally {
            try {
                Files.deleteIfExists(message);
            } catch (IOException ignored) {
                // A leftover temporary file is harmless.
            }
        }
    }

    /**
     * Rebases the checked out branch onto {@code onto}:
     * {@code git rebase --autostash --keep-empty --rebase-merges --no-autosquash <onto>}. Merges are recreated instead of
     * flattened, and fixup commits stay where they are whatever {@code rebase.autoSquash} says. When the
     * rebase fails, for instance on a conflict, it is aborted again, so the repository is left as it was.
     *
     * @return the command lines that were run
     * @throws GitCommandException if git refuses, with git's explanation as message
     */
    public static List<String> onto(Path workTree, String onto) {
        List<String> executed = new ArrayList<>();
        try {
            executed.add(CliCheckout.exec(workTree, "git", "rebase", "--autostash", "--keep-empty", "--rebase-merges",
                    "--no-autosquash", onto).command());
        } catch (GitCommandException e) {
            executed.addAll(e.commands());
            try {
                executed.add(CliCheckout.exec(workTree, "git", "rebase", "--abort").command());
            } catch (GitCommandException abortFailure) {
                executed.addAll(abortFailure.commands());
            }
            throw new GitCommandException(e.getMessage(), executed, e);
        }
        return executed;
    }

    /**
     * Squashes the commit {@code sha} into its parent with an interactive rebase that is told to turn that
     * commit's {@code pick} into {@code squash} and to keep the combined message as git proposes it:
     * {@code git rebase --interactive --autostash --keep-empty --rebase-merges --no-autosquash <parent>^}, or
     * {@code --root} when the parent has no parent. Uncommitted changes are stashed and brought back. When the
     * rebase fails, it is aborted again, so the repository is left as it was.
     *
     * @param sha the full object id of a commit that has exactly one parent
     * @return the command lines that were run
     * @throws GitCommandException if git refuses, with git's explanation as message
     */
    public static List<String> squash(Path workTree, String sha) {
        String parent = CliCheckout.run(workTree, "git", "rev-parse", sha + "^").strip();
        String abbreviated = sha.substring(0, Math.min(7, sha.length()));
        Map<String, String> environment = Map.of(
                "GIT_SEQUENCE_EDITOR", "sed -i -e 's/^pick " + abbreviated + "/squash " + abbreviated + "/'",
                "GIT_EDITOR", "true");
        String base = hasParent(workTree, parent) ? parent + "^" : "--root";
        List<String> executed = new ArrayList<>();
        try {
            executed.add(CliCheckout.exec(workTree, environment, "git", "rebase", "--interactive",
                    "--autostash", "--keep-empty", "--rebase-merges", "--no-autosquash", base).command());
        } catch (GitCommandException e) {
            executed.addAll(e.commands());
            try {
                executed.add(CliCheckout.exec(workTree, "git", "rebase", "--abort").command());
            } catch (GitCommandException abortFailure) {
                executed.addAll(abortFailure.commands());
            }
            throw new GitCommandException(e.getMessage(), executed, e);
        }
        return executed;
    }

    private static boolean hasParent(Path workTree, String sha) {
        // "<sha> <parents...>": only the sha when it is a root commit.
        return CliCheckout.run(workTree, "git", "rev-list", "--parents", "-n", "1", sha).strip().contains(" ");
    }

    private static Path writeMessage(String summary, String description) {
        try {
            Path file = Files.createTempFile("jazygit-message", ".txt");
            Files.writeString(file, description.isBlank() ? summary + "\n" : summary + "\n\n" + description + "\n");
            return file;
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
