package net.vanfleteren.jazygit.askpass;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CompletableFuture;
import net.vanfleteren.jazygit.git.branches.CliRemotes;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * The whole way (ADR 150): git runs a fake ssh, which says it is OpenSSH 9.6 and runs {@code SSH_ASKPASS} as ssh does;
 * that is the launcher of the server, which runs the helper on this {@code java}; the helper asks the server, which
 * asks the user.
 */
class AskpassChainTest {

    @Test
    void sshGetsWhatTheUserTyped(@TempDir Path dir) throws Exception {
        Path repo = behindFakeSsh(dir);
        try (AskpassServer server = AskpassServer.in(dir)) {
            server.answeredBy(new Answering("s3cret"));

            GitFailure failure =
                    new CliRemotes(repo, HashMap::empty, server).pull().getLeft();

            assertTrue(
                    failure.error() instanceof GitError.CommandFailed failed
                            && failed.output().contains("ssh was told: s3cret"),
                    failure.error().toString());
        }
    }

    private static Path behindFakeSsh(Path dir) throws Exception {
        Path repo = dir.resolve("repo");
        Path ssh = dir.resolve("ssh.sh");
        Files.writeString(
                ssh,
                "#!/bin/sh\n"
                        + "if [ \"$1\" = -V ]; then echo 'OpenSSH_9.6p1, OpenSSL 3.0.13' >&2; exit 0; fi\n"
                        + "answer=$(\"$SSH_ASKPASS\" \"Enter passphrase for key '/home/u/.ssh/id': \")\n"
                        + "echo \"ssh was told: $answer\" >&2\n"
                        + "exit 255\n");
        Files.setPosixFilePermissions(ssh, PosixFilePermissions.fromString("rwx------"));
        run(dir, "git", "init", "-q", "-b", "main", repo.toString());
        run(repo, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "first");
        run(repo, "git", "remote", "add", "origin", "git@example.invalid:owner/repo.git");
        run(repo, "git", "config", "core.sshCommand", ssh.toString());
        run(repo, "git", "config", "branch.main.remote", "origin");
        run(repo, "git", "config", "branch.main.merge", "refs/heads/main");
        return repo;
    }

    private static void run(Path in, String... command) throws Exception {
        new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .exitValueNormal()
                .execute();
    }

    /**
     * The user, who types {@code typed} for whatever is asked.
     */
    private record Answering(String typed) implements Asking {

        @Override
        public CompletableFuture<Option<SecretBuffer>> ask(long request, String prompt) {
            SecretBuffer buffer = new SecretBuffer();
            typed.codePoints().forEach(buffer::type);
            return CompletableFuture.completedFuture(Option.of(buffer));
        }

        @Override
        public void withdraw(long request) {}
    }
}
