package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the diff of changed files through the git command line tool.
 */
public final class CliDiff {

    private static final long TIMEOUT_SECONDS = 30;
    static final int MAX_LINES = 5000;

    private CliDiff() {
    }

    /**
     * The staged changes of the files (index against HEAD), and their unstaged changes (working tree
     * against the index). Untracked files are shown as entirely added. Long diffs are cut off.
     */
    public static Diffs diff(Path workTree, List<FileEntry> files) {
        List<String> staged = paths(files, f -> f.staged() && f.type() != ChangeType.UNTRACKED);
        List<String> unstaged = paths(files, f -> f.unstaged() && f.type() != ChangeType.UNTRACKED);
        Stream<String> untracked = files.stream()
                .filter(f -> f.type() == ChangeType.UNTRACKED)
                .map(f -> run(workTree, List.of("diff", "--no-index", "--no-color", "--no-ext-diff", "--",
                        "/dev/null"), List.of(f.path())));
        return new Diffs(
                truncated(tracked(workTree, "--cached", staged)),
                truncated(Stream.concat(Stream.of(tracked(workTree, "--", unstaged)), untracked)
                        .collect(Collectors.joining())));
    }

    private static List<String> paths(List<FileEntry> files, Predicate<FileEntry> filter) {
        return files.stream().filter(filter).map(FileEntry::path).toList();
    }

    private static String tracked(Path workTree, String mode, List<String> paths) {
        if (paths.isEmpty()) {
            return "";
        }
        List<String> subcommand = mode.equals("--")
                ? List.of("diff", "--no-color", "--no-ext-diff", "--")
                : List.of("diff", "--cached", "--no-color", "--no-ext-diff", "--");
        return run(workTree, subcommand, paths);
    }

    static String truncated(String diff) {
        List<String> lines = diff.lines().toList();
        return lines.size() <= MAX_LINES
                ? diff
                : Stream.concat(lines.stream().limit(MAX_LINES), Stream.of("… truncated"))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    /**
     * Exit code 1 is fine: {@code --no-index} uses it to say the files differ.
     */
    private static String run(Path workTree, List<String> subcommand, List<String> paths) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(subcommand);
        command.addAll(paths);
        try {
            ProcessResult result = new ProcessExecutor()
                    .command(command)
                    .directory(workTree.toFile())
                    .readOutput(true)
                    .redirectErrorStream(true)
                    .exitValueAny()
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute();
            if (result.getExitValue() > 1) {
                throw new IllegalStateException(
                        CliCheckout.explanation(result.outputUTF8(), result.getExitValue()));
            }
            return result.outputUTF8();
        } catch (IOException e) {
            throw new IllegalStateException("Could not run git; is it installed and on the PATH?", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("git diff timed out in " + workTree, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git diff", e);
        }
    }
}
