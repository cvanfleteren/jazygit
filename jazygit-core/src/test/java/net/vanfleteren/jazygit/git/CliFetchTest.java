package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Fetching from the remotes.
 */
class CliFetchTest {

    @TempDir
    Path dir;

    private static String git(Path in, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }

    @Test
    void fetchAllBringsInWhatWasPushedToEveryRemote() throws Exception {
        Path origin = dir.resolve("origin.git");
        Path other = dir.resolve("other.git");
        Path repo = dir.resolve("repo");
        Path clone = dir.resolve("clone");
        git(dir, "init", "-q", "--bare", "-b", "main", origin.toString());
        git(dir, "init", "-q", "--bare", "-b", "main", other.toString());
        git(dir, "init", "-q", "-b", "main", repo.toString());
        git(repo, "remote", "add", "origin", origin.toString());
        git(repo, "remote", "add", "other", other.toString());
        git(dir, "clone", "-q", origin.toString(), clone.toString());
        git(clone, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "theirs");
        git(clone, "push", "-q", "origin", "main");
        git(clone, "push", "-q", other.toString(), "main");

        git(repo, "remote", "add", "skipped", dir.resolve("nowhere.git").toString());
        git(repo, "config", "remote.skipped.skipFetchAll", "true");

        List<String> remotes = CliRemote.remotes(repo).get();
        assertEquals(List.of("origin", "other"), remotes);
        assertEquals(
                List.of("git fetch --quiet --prune --no-write-fetch-head origin"),
                CliFetch.fetch(repo, "origin", Option.none(), HashMap.empty())
                        .get()
                        .map(ExecutedCommand::line));
        assertEquals(
                List.of("git fetch --quiet --prune --no-write-fetch-head other"),
                CliFetch.fetch(repo, "other", Option.none(), HashMap.empty())
                        .get()
                        .map(ExecutedCommand::line));

        assertTrue(git(repo, "branch", "-r").contains("origin/main"));
        assertTrue(git(repo, "branch", "-r").contains("other/main"));
    }

    @Test
    void aBranchDeletedOnTheRemoteIsPrunedEvenWhenGitIsToldNotTo() throws Exception {
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        git(dir, "init", "-q", "--bare", "-b", "main", origin.toString());
        git(dir, "clone", "-q", origin.toString(), repo.toString());
        git(repo, "config", "fetch.prune", "false");
        git(repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "first");
        git(repo, "push", "-q", "-u", "origin", "main", "HEAD:refs/heads/feature");
        git(repo, "branch", "-q", "--track", "feature", "origin/feature");
        git(origin, "branch", "-q", "-D", "feature");

        CliFetch.fetch(repo, "origin", Option.none(), HashMap.empty()).get();

        assertFalse(git(repo, "branch", "-r").contains("origin/feature"));
        assertTrue(git(repo, "branch", "-vv").contains("[origin/feature: gone]"));
    }

    @Test
    void theFetchLeavesFetchHeadAsThePullsOwnFetchWroteIt() throws Exception {
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        Path clone = dir.resolve("clone");
        git(dir, "init", "-q", "--bare", "-b", "main", origin.toString());
        git(dir, "clone", "-q", origin.toString(), repo.toString());
        git(repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "first");
        git(repo, "push", "-q", "-u", "origin", "main");
        git(repo, "fetch", "-q", "origin");
        Path fetchHead = repo.resolve(".git/FETCH_HEAD");
        String pulled = Files.readString(fetchHead);
        git(dir, "clone", "-q", origin.toString(), clone.toString());
        git(clone, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "theirs");
        git(clone, "push", "-q", "origin", "main");

        CliFetch.fetch(repo, "origin", Option.none(), HashMap.empty()).get();

        assertEquals(pulled, Files.readString(fetchHead));
        assertEquals(git(clone, "rev-parse", "main"), git(repo, "rev-parse", "origin/main"));
    }
}
