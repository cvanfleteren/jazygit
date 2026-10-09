package net.vanfleteren.jazygit.model;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the diff of changed files through the git command line tool.
 */
public final class GitCliDiff {

    private static final long TIMEOUT_SECONDS = 30;
    static final int MAX_LINES = 5000;

    private GitCliDiff() {
    }

    /**
     * The diff of the files against HEAD, staged and unstaged changes together. Untracked files are
     * shown as entirely added. Long diffs are cut off.
     */
    public static String diff(Path workTree, List<FileEntry> files) {
        List<String> tracked = files.stream()
                .filter(f -> f.type() != ChangeType.UNTRACKED)
                .map(FileEntry::path)
                .toList();
        Stream<String> trackedDiff = tracked.isEmpty()
                ? Stream.empty()
                : Stream.of(run(workTree, List.of("diff", "HEAD", "--no-color", "--no-ext-diff", "--"), tracked));
        Stream<String> untrackedDiffs = files.stream()
                .filter(f -> f.type() == ChangeType.UNTRACKED)
                .map(f -> run(workTree, List.of("diff", "--no-index", "--no-color", "--no-ext-diff", "--", "/dev/null"),
                        List.of(f.path())));
        return truncated(Stream.concat(trackedDiff, untrackedDiffs).collect(Collectors.joining()));
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
                        GitCliCheckout.explanation(result.outputUTF8(), result.getExitValue()));
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
