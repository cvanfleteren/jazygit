package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fetching from the remotes.
 */
class CliRemoteTest {

    @TempDir
    Path dir;

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
        assertEquals(List.of("git fetch --quiet origin"), CliRemote.fetch(repo, "origin", Option.none()).get());
        assertEquals(List.of("git fetch --quiet other"), CliRemote.fetch(repo, "other", Option.none()).get());

        assertTrue(git(repo, "branch", "-r").contains("origin/main"));
        assertTrue(git(repo, "branch", "-r").contains("other/main"));
    }

    @Test
    void remotesWithSkipFetchAllAreLeftOut() {
        assertEquals(List.of("Mirror", "old"),
                CliRemote.skipped("remote.Mirror.skipfetchall true\nremote.origin.skipfetchall false\nremote.old.skipfetchall true\n"));
    }

    @Test
    void sshRunsInBatchModeKeepingTheConfiguredCommand() {
        assertEquals("ssh -o BatchMode=yes", CliRemote.batchMode(Option.none()));
        assertEquals("ssh -i ~/.ssh/work -o BatchMode=yes", CliRemote.batchMode(Option.of("ssh -i ~/.ssh/work")));
    }

    private static String git(Path in, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor().command(command).directory(in.toFile())
                .readOutput(true).redirectErrorStream(true).exitValueNormal().execute().outputUTF8();
    }
}
