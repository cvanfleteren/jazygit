package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        List<String> untracked = paths(files, f -> f.type() == ChangeType.UNTRACKED);
        return new Diffs(
                truncated(tracked(workTree, List.of("diff", "--cached", "--no-color", "--no-ext-diff", "--"),
                        staged, Map.of())),
                truncated(unstagedAndUntracked(workTree, unstaged, untracked)));
    }

    private static List<String> paths(List<FileEntry> files, Predicate<FileEntry> filter) {
        return files.stream().filter(filter).map(FileEntry::path).toList();
    }

    /**
     * Untracked files are marked intent-to-add in a temporary copy of the index, so one {@code git diff}
     * shows them as entirely added next to the unstaged changes. The real index is left untouched.
     */
    private static String unstagedAndUntracked(Path workTree, List<String> unstaged, List<String> untracked) {
        List<String> diff = List.of("diff", "--no-color", "--no-ext-diff", "--");
        if (untracked.isEmpty()) {
            return tracked(workTree, diff, unstaged, Map.of());
        }
        Path tempIndex = null;
        try {
            Path index = workTree.resolve(run(workTree, List.of("rev-parse", "--git-path", "index"), List.of()).strip());
            tempIndex = Files.createTempFile("jazygit-index", null);
            if (Files.exists(index)) {
                Files.copy(index, tempIndex, StandardCopyOption.REPLACE_EXISTING);
            }
            Map<String, String> env = Map.of("GIT_INDEX_FILE", tempIndex.toString());
            run(workTree, List.of("add", "--intent-to-add", "--"), untracked, env);
            return tracked(workTree, diff, Stream.concat(unstaged.stream(), untracked.stream()).toList(), env);
        } catch (IOException e) {
            throw new IllegalStateException(Messages.get("git.notInstalled"), e);
        } finally {
            if (tempIndex != null) {
                try {
                    Files.deleteIfExists(tempIndex);
                } catch (IOException ignored) {
                    // a leftover temp file is harmless
                }
            }
        }
    }

    private static String tracked(Path workTree, List<String> subcommand, List<String> paths,
                                  Map<String, String> env) {
        return paths.isEmpty() ? "" : run(workTree, subcommand, paths, env);
    }

    static String truncated(String diff) {
        List<String> lines = diff.lines().toList();
        return lines.size() <= MAX_LINES
                ? diff
                : Stream.concat(lines.stream().limit(MAX_LINES), Stream.of(Messages.get("git.diff.truncated")))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    /**
     * Exit code 1 is fine: {@code --no-index} uses it to say the files differ.
     */
    static String run(Path workTree, List<String> subcommand, List<String> paths) {
        return run(workTree, subcommand, paths, Map.of());
    }

    static String run(Path workTree, List<String> subcommand, List<String> paths,
                      Map<String, String> env) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(subcommand);
        command.addAll(paths);
        try {
            ProcessResult result = new ProcessExecutor()
                    .command(command)
                    .directory(workTree.toFile())
                    .environment(env)
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
            throw new IllegalStateException(Messages.get("git.notInstalled"), e);
        } catch (TimeoutException e) {
            throw new IllegalStateException(Messages.get("git.timedOut", "diff", workTree), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(Messages.get("git.interrupted", "diff"), e);
        }
    }
}
