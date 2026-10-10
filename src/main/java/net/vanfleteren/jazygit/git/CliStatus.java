package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads the working tree status through the {@code git status} command line tool.
 */
public final class CliStatus {

    private static final long TIMEOUT_SECONDS = 10;

    private CliStatus() {
    }

    /**
     * Runs {@code git status} in {@code workTree} and parses its output.
     *
     * @throws IllegalStateException if git cannot be run or reports an error
     */
    public static RepoStatus read(Path workTree) {
        try {
            String output = new ProcessExecutor()
                    .command("git", "--no-optional-locks", "status",
                            "--porcelain=v2", "--branch", "-z", "--untracked-files=all")
                    .directory(workTree.toFile())
                    .readOutput(true)
                    // readOutput would otherwise capture stderr too, mixing git warnings into the status.
                    .redirectError(OutputStream.nullOutputStream())
                    .exitValueNormal()
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute()
                    .outputUTF8();
            return parse(output);
        } catch (IOException e) {
            throw new IllegalStateException(Messages.get("git.notInstalled"), e);
        } catch (InvalidExitValueException e) {
            throw new IllegalStateException(Messages.get("git.failed", "status", workTree, String.valueOf(e.getExitValue())), e);
        } catch (TimeoutException e) {
            throw new IllegalStateException(Messages.get("git.timedOut", "status", workTree), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(Messages.get("git.interrupted", "status"), e);
        }
    }

    /**
     * Parses the output of {@code git status --porcelain=v2 --branch -z}.
     */
    static RepoStatus parse(String raw) {
        String head = "";
        String oid = "";
        Map<String, FileEntry> byPath = new TreeMap<>();
        String[] fields = raw.split("\0");
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            if (field.isEmpty()) {
                continue;
            }
            switch (field.charAt(0)) {
                case '#' -> {
                    if (field.startsWith("# branch.head ")) {
                        head = field.substring("# branch.head ".length());
                    } else if (field.startsWith("# branch.oid ")) {
                        oid = field.substring("# branch.oid ".length());
                    }
                }
                case '?' -> put(byPath, new FileEntry(field.substring(2), ChangeType.UNTRACKED, false, true));
                // 1 XY sub mH mI mW hH hI path
                case '1' -> {
                    String xy = field.substring(2, 4);
                    put(byPath, new FileEntry(pathAfter(field, 8), ordinaryChange(xy),
                            xy.charAt(0) != '.', xy.charAt(1) != '.'));
                }
                // 2 XY sub mH mI mW hH hI Xscore path, followed by the original path as its own field
                case '2' -> {
                    String xy = field.substring(2, 4);
                    boolean staged = xy.charAt(0) != '.';
                    boolean unstaged = xy.charAt(1) != '.';
                    put(byPath, new FileEntry(pathAfter(field, 9), ChangeType.ADDED, staged, unstaged));
                    if (i + 1 < fields.length) {
                        put(byPath, new FileEntry(fields[++i], ChangeType.DELETED, staged, unstaged));
                    }
                }
                // u XY sub m1 m2 m3 mW h1 h2 h3 path
                case 'u' -> put(byPath, FileEntry.conflicted(pathAfter(field, 10), Conflict.of(field.substring(2, 4))));
                default -> {
                    // Ignored entries ('!') and anything unknown are not shown.
                }
            }
        }
        return new RepoStatus(head, oid, List.copyOf(byPath.values()));
    }

    private static void put(Map<String, FileEntry> byPath, FileEntry entry) {
        byPath.put(entry.path(), entry);
    }

    private static ChangeType ordinaryChange(String xy) {
        if (xy.indexOf('D') >= 0) {
            return ChangeType.DELETED;
        }
        if (xy.charAt(0) == 'A') {
            return ChangeType.ADDED;
        }
        return ChangeType.MODIFIED;
    }

    /**
     * Returns everything after the first {@code spaces} space-separated fields; paths may contain spaces.
     */
    private static String pathAfter(String field, int spaces) {
        int index = 0;
        for (int n = 0; n < spaces; n++) {
            index = field.indexOf(' ', index) + 1;
        }
        return field.substring(index);
    }
}
