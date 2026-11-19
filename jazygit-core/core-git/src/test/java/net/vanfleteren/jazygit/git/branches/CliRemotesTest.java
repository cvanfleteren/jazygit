package net.vanfleteren.jazygit.git.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.SshAsking;
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
     * ssh in batch mode skips a key that has a passphrase but is not in the agent, and the server then refuses it; git
     * only adds that it could not read from the remote. The failure says what ssh refused.
     */
    @Test
    void aKeyTheServerRefusesIsToldApartFromOtherFailures(@TempDir Path dir) throws Exception {
        Path repo = behindFakeSsh(dir, "git@example.invalid: Permission denied (publickey).");
        Option<GitError> refused =
                Option.of(new GitError.SshKeyRefused("git@example.invalid: Permission denied (publickey)."));

        assertEquals(
                refused, failedWith(CliRemotes.fetch(repo, new RemoteName("origin"), Option.none(), HashMap.empty())));
        assertEquals(
                refused, failedWith(CliRemotes.run(repo, GitCommand.git("push", "origin", "main"), HashMap.empty())));
        assertEquals(refused, failedWith(new CliRemotes(repo, HashMap::empty).pull()));
    }

    /**
     * Only a refused key is told the user to add it to the agent; ssh failing otherwise is git's failure as it is.
     */
    @Test
    void anotherSshFailureIsNoRefusedKey(@TempDir Path dir) throws Exception {
        Path repo = behindFakeSsh(dir, "ssh: Could not resolve hostname example.invalid: Name or service not known");

        assertTrue(failedWith(CliRemotes.fetch(repo, new RemoteName("origin"), Option.none(), HashMap.empty()))
                .exists(error -> error instanceof GitError.CommandFailed));
        assertTrue(failedWith(new CliRemotes(repo, HashMap::empty).pull())
                .exists(error -> error instanceof GitError.CommandFailed));
    }

    /**
     * What the user starts runs ssh with what {@link SshAsking} sets, which the fake ssh says back, as one operation
     * each, renaming an upstream too, which fetches and then pushes; a fetch runs in batch mode, without asking.
     */
    @Test
    void whatTheUserStartsLetsSshAskAndAFetchDoesNot(@TempDir Path dir) throws Exception {
        Path repo = behindSayingSsh(dir);
        Asking asking = new Asking(Option.none());
        CliRemotes remotes = new CliRemotes(repo, HashMap::empty, asking);
        RemoteBranch main = new RemoteBranch("origin", new BranchName("main"));
        Option<GitError> refused = Option.of(new GitError.SshKeyRefused("asking: Permission denied (publickey)."));

        assertEquals(refused, failedWith(remotes.pull()));
        assertEquals(refused, failedWith(remotes.pushSettingUpstream(new BranchName("main"), main)));
        assertEquals(refused, failedWith(remotes.deleteRemoteBranch(main)));
        assertEquals(
                Option.of(new GitError.UpstreamNotRenamed(main, new BranchName("renamed"), refused.get())),
                failedWith(remotes.renameUpstream(new BranchName("renamed"), main)));
        assertEquals(
                Option.of(new GitError.SshKeyRefused(": Permission denied (publickey).")),
                failedWith(remotes.fetch(new RemoteName("origin"))));

        assertEquals(java.util.List.of(repo, repo, repo, repo), asking.asked);
    }

    /**
     * A passphrase the user cancelled fails as a refused key, whose hint to add the key to the agent holds; anything
     * else ssh asked, as cancelled.
     */
    @Test
    void whatTheUserCancelledSaysWhy(@TempDir Path dir) throws Exception {
        Path repo = behindSayingSsh(dir);
        SshAsking.Cancelled passphrase = new SshAsking.Cancelled("Enter passphrase for key '/home/u/.ssh/id': ");
        SshAsking.Cancelled code = new SshAsking.Cancelled("Verification code: ");

        assertEquals(
                Option.of(new GitError.SshKeyRefused("asking: Permission denied (publickey).")),
                failedWith(new CliRemotes(repo, HashMap::empty, new Asking(Option.of(passphrase))).pull()));
        assertEquals(
                Option.of(new GitError.SshCancelled("Verification code:")),
                failedWith(new CliRemotes(repo, HashMap::empty, new Asking(Option.of(code))).pull()));
    }

    @Test
    void aPromptForThePassphraseOfAKeyIsToldApart() {
        assertTrue(new SshAsking.Cancelled("Enter passphrase for key '/home/u/.ssh/id_ed25519': ").passphrase());
        assertTrue(!new SshAsking.Cancelled("git@github.com's password: ").passphrase());
    }

    /**
     * A repository whose {@code main} follows {@code origin/main}, on a remote reached through a fake ssh that says
     * {@code JAZYGIT_SAID} as it refuses the key, as ssh says that it was refused.
     */
    private static Path behindSayingSsh(Path dir) throws Exception {
        Path repo = behindFakeSsh(dir, "unused");
        Files.writeString(
                dir.resolve("ssh.sh"),
                "#!/bin/sh\necho \"$JAZYGIT_SAID: Permission denied (publickey).\" >&2\nexit 255\n");
        return repo;
    }

    /**
     * An {@link SshAsking} that sets {@code JAZYGIT_SAID} to {@code asking}, and keeps the directories it was asked
     * for; as if the user cancelled {@code cancelled}, if anything.
     */
    private static final class Asking implements SshAsking {

        private final Option<Cancelled> cancelled;
        private final java.util.List<Path> asked = new java.util.ArrayList<>();

        Asking(Option<Cancelled> cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public <T> Asked<T> asking(Path directory, Function<Map<String, String>, T> talk) {
            asked.add(directory);
            return new Asked<>(talk.apply(HashMap.of("JAZYGIT_SAID", "asking")), cancelled);
        }
    }

    /**
     * A repository whose {@code main} follows {@code origin/main}, on a remote reached through a fake ssh that prints
     * {@code said} and fails.
     */
    private static Path behindFakeSsh(Path dir, String said) throws Exception {
        Path repo = dir.resolve("repo");
        Path ssh = dir.resolve("ssh.sh");
        Files.writeString(ssh, "#!/bin/sh\necho '" + said + "' >&2\nexit 255\n");
        Files.setPosixFilePermissions(ssh, PosixFilePermissions.fromString("rwxr-xr-x"));
        run(dir, "git", "init", "-q", "-b", "main", repo.toString());
        run(repo, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "first");
        run(repo, "git", "remote", "add", "origin", "git@example.invalid:owner/repo.git");
        run(repo, "git", "config", "core.sshCommand", ssh.toString());
        run(repo, "git", "config", "branch.main.remote", "origin");
        run(repo, "git", "config", "branch.main.merge", "refs/heads/main");
        return repo;
    }

    private static Option<GitError> failedWith(Either<GitFailure, ?> result) {
        return result.swap().map(GitFailure::error).toOption();
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
