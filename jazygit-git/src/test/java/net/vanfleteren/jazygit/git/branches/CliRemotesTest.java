package net.vanfleteren.jazygit.git.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.process.GitCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Talking to the remotes.
 */
class CliRemotesTest {

    @Test
    void remotesWithSkipFetchAllAreLeftOut() {
        assertEquals(
                List.of("Mirror", "old"),
                CliRemotes.skipped(
                        "remote.Mirror.skipfetchall true\nremote.origin.skipfetchall false\nremote.old.skipfetchall true\n"));
    }

    @Test
    void theUrlOfEachRemoteIsReadByItsName() {
        assertEquals(
                HashMap.of("origin", "git@github.com:owner/repo.git", "work.mirror", "https://example.com/repo")
                        .mapKeys(RemoteName::new),
                CliRemotes.parseUrls("remote.origin.url git@github.com:owner/repo.git\n"
                        + "remote.work.mirror.url https://example.com/repo\n"
                        + "remote.origin.url https://github.com/owner/other\n"));
    }

    @Test
    void theCredentialsAreSetForCommandsThatTalkToARemote(@TempDir Path repo) {
        GitCommand fetch = CliRemotes.toRemote(
                        repo, GitCommand.git("fetch", "origin"), Option.of("ssh"), HashMap.of("GH_TOKEN", "secret"))
                .get();

        assertEquals(Option.of("secret"), fetch.environment().get("GH_TOKEN"));
        assertEquals(Option.of("ssh -o BatchMode=yes"), fetch.environment().get("GIT_SSH_COMMAND"));
    }

    /**
     * The other end of a remote on disk logs what the credentials set, so a fetch and a push show what git was given;
     * and the credentials are asked again for each.
     */
    @Test
    void theRolePassesTheCredentialsItIsGivenToGitEachTimeItTalksToARemote(@TempDir Path dir) throws Exception {
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        Path log = dir.resolve("probe.log");
        run(dir, "git", "init", "-q", "--bare", "-b", "main", origin.toString());
        run(dir, "git", "init", "-q", "-b", "main", repo.toString());
        run(repo, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "first");
        run(repo, "git", "remote", "add", "origin", origin.toString());
        run(
                repo,
                "git",
                "config",
                "remote.origin.uploadpack",
                probe(dir, "upload-pack", log).toString());
        run(
                repo,
                "git",
                "config",
                "remote.origin.receivepack",
                probe(dir, "receive-pack", log).toString());
        AtomicInteger asked = new AtomicInteger();
        CliRemotes remotes = new CliRemotes(repo, () -> HashMap.of("JAZYGIT_PROBE", "call " + asked.incrementAndGet()));

        assertTrue(
                remotes.pushSettingUpstream(new BranchName("main"), new RemoteBranch("origin", new BranchName("main")))
                        .isRight());
        assertTrue(remotes.fetch(new RemoteName("origin")).isRight());

        assertEquals(2, asked.get());
        assertEquals(java.util.List.of("receive-pack call 1", "upload-pack call 2"), Files.readAllLines(log));
    }

    /**
     * A script that git runs as the {@code command} of the remote: it logs the variable the credentials set, then is
     * that command.
     */
    private static Path probe(Path dir, String command, Path log) throws Exception {
        Path script = dir.resolve(command + ".sh");
        Files.writeString(
                script,
                "#!/bin/sh\necho \"" + command + " $JAZYGIT_PROBE\" >> '" + log + "'\nexec git " + command
                        + " \"$@\"\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private static void run(Path in, String... command) throws Exception {
        new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .exitValueNormal()
                .execute();
    }

    @Test
    void theSshCommandOfTheConfigIsKeptWhenTheEnvironmentSetsNone(@TempDir Path repo) throws Exception {
        configureSsh(repo, "ssh -i ~/.ssh/work");

        assertEquals(Option.of("ssh -i ~/.ssh/work -o BatchMode=yes"), sshCommand(repo, Option.none()));
        assertEquals(Option.of("ssh -i ~/.ssh/work -o BatchMode=yes"), sshCommand(repo, Option.of("  ")));
    }

    @Test
    void theSshCommandOfTheEnvironmentGoesBeforeTheConfig(@TempDir Path repo) throws Exception {
        configureSsh(repo, "ssh -i ~/.ssh/work");

        assertEquals(Option.of("ssh -i ~/.ssh/own -o BatchMode=yes"), sshCommand(repo, Option.of("ssh -i ~/.ssh/own")));
    }

    @Test
    void anEmptySshCommandInTheConfigRunsPlainSsh(@TempDir Path repo) throws Exception {
        configureSsh(repo, "");

        assertEquals(Option.of("ssh -o BatchMode=yes"), sshCommand(repo, Option.none()));
    }

    @Test
    void sshRunsInBatchModeKeepingTheConfiguredCommand() {
        assertEquals("ssh -o BatchMode=yes", CliRemotes.batchMode(Option.none()));
        assertEquals("ssh -i ~/.ssh/work -o BatchMode=yes", CliRemotes.batchMode(Option.of("ssh -i ~/.ssh/work")));
    }

    private static Option<String> sshCommand(Path repo, Option<String> environment) {
        return CliRemotes.toRemote(repo, GitCommand.git("fetch", "origin"), environment, HashMap.empty())
                .get()
                .environment()
                .get("GIT_SSH_COMMAND");
    }

    private static void configureSsh(Path repo, String command) throws Exception {
        new ProcessExecutor()
                .command("git", "init", "-q", repo.toString())
                .exitValueNormal()
                .execute();
        new ProcessExecutor()
                .command("git", "-C", repo.toString(), "config", "core.sshCommand", command)
                .exitValueNormal()
                .execute();
    }
}
