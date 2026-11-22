package net.vanfleteren.jazygit.core.askpass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.process.SshAsking;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * ssh asking the user through jazygit (ADR 150): the helper, run in this process, passes what ssh asks over the socket
 * to the server, which asks the user as a fake {@link Asking} answers.
 */
class AskpassServerTest {

    private static final String PASSPHRASE = "Enter passphrase for key '/home/u/.ssh/id_ed25519': ";

    @TempDir
    Path temporary;

    private final User user = new User();
    private AskpassServer server;

    @AfterEach
    void close() {
        Option.of(server).forEach(AskpassServer::close);
    }

    @Test
    void aSecretIsAnsweredAsTheUserTypedIt() {
        start(true);
        user.answer("pässword");

        SshAsking.Asked<Tuple2<Integer, String>> asked = server.asking(temporary, helper("", PASSPHRASE));

        assertEquals(Tuple.of(0, "pässword\n"), asked.result());
        assertEquals(Option.none(), asked.cancelled());
        assertEquals(java.util.List.of(PASSPHRASE), user.asked);
    }

    @Test
    void aCancelRefusesAndIsToldWithTheRestOfTheOperation() {
        start(true);
        user.cancel();

        SshAsking.Asked<Tuple2<Integer, String>> asked = server.asking(temporary, environment -> {
            Tuple2<Integer, String> first = helper("", PASSPHRASE).apply(environment);
            // ssh tries a password next: refused without asking.
            Tuple2<Integer, String> second =
                    helper("", "git@example.com's password: ").apply(environment);
            assertEquals(Tuple.of(1, ""), second);
            return first;
        });

        assertEquals(Tuple.of(1, ""), asked.result());
        assertEquals(Option.of(new SshAsking.Cancelled(PASSPHRASE)), asked.cancelled());
        assertEquals(java.util.List.of(PASSPHRASE), user.asked);
    }

    @Test
    void aYesIsRefusedWithoutAsking() {
        start(true);

        assertEquals(
                Tuple.of(1, ""),
                server.asking(temporary, helper("confirm", "Are you sure you want to continue connecting?"))
                        .result());
        assertTrue(user.asked.isEmpty());
    }

    @Test
    void whatSshOnlyShowsWhileItWaitsIsAnsweredAtOnce() {
        start(true);

        assertEquals(
                Tuple.of(0, "\n"),
                server.asking(temporary, helper("none", "Confirm user presence for key ED25519-SK"))
                        .result());
        assertTrue(user.asked.isEmpty());
    }

    @Test
    void aRequestOutsideAnOperationIsRefused() {
        start(true);
        Map<String, String> ended =
                server.asking(temporary, environment -> environment).result();

        assertEquals(Tuple.of(1, ""), helper("", PASSPHRASE).apply(ended));
        assertEquals(Tuple.of(1, ""), helper("", PASSPHRASE).apply(HashMap.empty()));
        assertTrue(user.asked.isEmpty());
    }

    @Test
    void theQuestionOfAnOperationThatEndedIsWithdrawn() throws Exception {
        start(true);
        CompletableFuture<Tuple2<Integer, String>> waiting = new CompletableFuture<>();

        server.asking(temporary, environment -> {
            Thread.ofVirtual()
                    .start(() -> waiting.complete(helper("", PASSPHRASE).apply(environment)));
            user.waitForAQuestion();
            return null;
        });

        assertEquals(Tuple.of(1, ""), waiting.get(10, TimeUnit.SECONDS));
        assertEquals(java.util.List.of(1L), user.withdrawn);
    }

    @Test
    void sshThatCannotAskRunsInBatchMode() {
        start(false);

        Map<String, String> environment =
                server.asking(temporary, variables -> variables).result();

        // Whatever ssh the user's configuration names.
        assertTrue(environment.get("GIT_SSH_COMMAND").exists(ssh -> ssh.endsWith(" -o BatchMode=yes")));
        assertFalse(environment.containsKey("SSH_ASKPASS"));
    }

    @Test
    void sshIsToldToAskThroughTheHelperAndGitIsNot() {
        start(true);

        Map<String, String> environment =
                server.asking(temporary, variables -> variables).result();

        assertEquals(Option.of("/the/helper"), environment.get("SSH_ASKPASS"));
        assertEquals(Option.of("force"), environment.get("SSH_ASKPASS_REQUIRE"));
        assertTrue(environment
                .get(AskpassProtocol.VARIABLE)
                .exists(address -> address.endsWith(" " + server.socket().get())));
        assertFalse(environment.containsKey("GIT_SSH_COMMAND"));
    }

    @Test
    void eachOperationHasATokenOfItsOwn() {
        start(true);

        assertFalse(server.asking(temporary, variables -> variables)
                .result()
                .get(AskpassProtocol.VARIABLE)
                .equals(server.asking(temporary, variables -> variables)
                        .result()
                        .get(AskpassProtocol.VARIABLE)));
    }

    @Test
    void theDirectoryIsTheOwnersAndGoesWithTheServer() throws Exception {
        start(true);
        server.asking(temporary, variables -> variables);
        Path directory = server.socket().get().getParent();

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
        server.close();
        assertFalse(Files.exists(directory));
    }

    /**
     * The socket only opens for the first operation that lets ssh ask (ADR 150): a session that never talks to a
     * remote, or only with an ssh that cannot ask, leaves nothing in the temporary directory.
     */
    @Test
    void nothingListensUntilSshMayAsk() throws Exception {
        start(false);
        server.asking(temporary, variables -> variables);
        assertEquals(java.util.List.of(), sockets());
        assertEquals(Option.none(), server.socket());
        server.close();

        start(true);
        assertEquals(java.util.List.of(), sockets());
        server.asking(temporary, variables -> variables);
        server.asking(temporary, variables -> variables);
        assertEquals(1, sockets().size());
    }

    /**
     * A socket that cannot be opened leaves the operation in batch mode, and nothing behind; it is tried once.
     */
    @Test
    void aSocketThatCannotOpenKeepsBatchModeAndIsNotTriedAgain() throws Exception {
        java.util.concurrent.atomic.AtomicInteger tries = new java.util.concurrent.atomic.AtomicInteger();
        server = AskpassServer.in(
                temporary,
                directory -> {
                    tries.incrementAndGet();
                    return Try.failure(new java.io.IOException("no launcher"));
                },
                (d, ssh) -> true,
                name -> Option.none());

        server.asking(temporary, variables -> variables);
        Map<String, String> environment =
                server.asking(temporary, variables -> variables).result();

        assertTrue(environment.get("GIT_SSH_COMMAND").exists(ssh -> ssh.endsWith(" -o BatchMode=yes")));
        assertFalse(environment.containsKey("SSH_ASKPASS"));
        assertEquals(1, tries.get());
        assertEquals(java.util.List.of(), sockets());
    }

    /**
     * Once the app stopped, nothing listens again: an operation still running then keeps batch mode.
     */
    @Test
    void afterTheServerStoppedNothingListensAgain() throws Exception {
        start(true);
        server.close();

        Map<String, String> environment =
                server.asking(temporary, variables -> variables).result();

        assertFalse(environment.containsKey("SSH_ASKPASS"));
        assertEquals(java.util.List.of(), sockets());
    }

    /**
     * The directories of a socket in the temporary directory.
     */
    private java.util.List<Path> sockets() throws Exception {
        try (var entries = Files.list(temporary)) {
            return entries.filter(entry -> entry.getFileName().toString().startsWith("jg"))
                    .toList();
        }
    }

    /**
     * With only {@code GIT_SSH} set, git runs that program as ssh: a plink, which knows nothing of
     * {@code SSH_ASKPASS} and would ask on the terminal, keeps batch mode, and an OpenSSH asks.
     */
    @Test
    void theSshOfGitSshIsTheOneAskedItsVersion() throws Exception {
        Path plink = program("plink.sh", "echo 'plink: Release 0.81'");
        Path openSsh = program("openssh.sh", "echo 'OpenSSH_9.6p1, OpenSSL 3.0.13' >&2");
        Path repository = repository();

        start(OpenSsh::asks, name -> name.equals("GIT_SSH") ? Option.of(plink.toString()) : Option.none());
        assertFalse(server.asking(repository, variables -> variables).result().containsKey("SSH_ASKPASS"));
        server.close();

        start(OpenSsh::asks, name -> name.equals("GIT_SSH") ? Option.of(openSsh.toString()) : Option.none());
        assertTrue(server.asking(repository, variables -> variables).result().containsKey("SSH_ASKPASS"));
    }

    /**
     * Git asks through {@code GIT_ASKPASS}, else {@code core.askPass}, else {@code SSH_ASKPASS}, which jazygit takes
     * over for ssh: git is given what the user had in that order, and nothing, so not jazygit's helper, only when the
     * user had none.
     */
    @Test
    void gitStillAsksThroughWhatTheUserSetForIt() throws Exception {
        Path repository = repository();

        assertEquals(Option.of(""), gitAskPass(repository, name -> Option.none()));
        assertEquals(
                Option.of("/usr/lib/ssh/x11-ssh-askpass"),
                gitAskPass(
                        repository, name -> Option.when(name.equals("SSH_ASKPASS"), "/usr/lib/ssh/x11-ssh-askpass")));
        new ProcessExecutor()
                .command("git", "-C", repository.toString(), "config", "core.askPass", "/home/u/bin/vault-askpass")
                .exitValueNormal()
                .execute();
        assertEquals(
                Option.of("/home/u/bin/vault-askpass"),
                gitAskPass(
                        repository, name -> Option.when(name.equals("SSH_ASKPASS"), "/usr/lib/ssh/x11-ssh-askpass")));
        assertEquals(
                Option.of("/home/u/bin/own-askpass"),
                gitAskPass(repository, name -> Option.when(name.equals("GIT_ASKPASS"), "/home/u/bin/own-askpass")));
    }

    /**
     * What an operation that lets ssh ask sets {@code GIT_ASKPASS} to in {@code repository}, for jazygit started with
     * the variables {@code environment} gives.
     */
    private Option<String> gitAskPass(Path repository, Function<String, Option<String>> environment) {
        start((d, ssh) -> true, environment);
        Option<String> askPass =
                server.asking(repository, variables -> variables).result().get("GIT_ASKPASS");
        server.close();
        return askPass;
    }

    private void start(boolean asks) {
        start((d, ssh) -> asks, name -> Option.none());
    }

    private void start(BiPredicate<Path, List<String>> asks, Function<String, Option<String>> environment) {
        server = AskpassServer.in(temporary, directory -> Try.success(Path.of("/the/helper")), asks, environment);
        server.answeredBy(user);
    }

    /**
     * A program in the temporary directory that runs {@code script}.
     */
    private Path program(String name, String script) throws Exception {
        Path program = temporary.resolve(name);
        Files.writeString(program, "#!/bin/sh\n" + script + "\n");
        Files.setPosixFilePermissions(program, PosixFilePermissions.fromString("rwx------"));
        return program;
    }

    /**
     * A repository with no ssh command configured, so that git runs the {@code GIT_SSH} the environment gives.
     */
    private Path repository() throws Exception {
        Path repository = temporary.resolve("repo");
        new ProcessExecutor()
                .command("git", "init", "-q", repository.toString())
                .exitValueNormal()
                .execute();
        return repository;
    }

    /**
     * Runs the helper as ssh would, with the variables of an operation, asking {@code prompt}: how it exits, and what
     * it printed.
     */
    private static Function<Map<String, String>, Tuple2<Integer, String>> helper(String kind, String prompt) {
        return environment -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int exit = AskpassHelper.run(environment.get(AskpassProtocol.VARIABLE), kind, prompt, out);
            return Tuple.of(exit, out.toString(StandardCharsets.UTF_8));
        };
    }

    /**
     * The user, who answers what they are told to, or cancels; or leaves the question open.
     */
    private static final class User implements Asking {

        private final java.util.List<String> asked = new CopyOnWriteArrayList<>();
        private final java.util.List<Long> withdrawn = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Void> question = new CompletableFuture<>();
        private Option<String> answer = Option.none();
        private boolean answers;
        private volatile CompletableFuture<Option<SecretBuffer>> open = new CompletableFuture<>();

        void answer(String typed) {
            answer = Option.of(typed);
            answers = true;
        }

        void cancel() {
            answers = true;
        }

        void waitForAQuestion() {
            question.orTimeout(10, TimeUnit.SECONDS).join();
        }

        @Override
        public CompletableFuture<Option<SecretBuffer>> ask(long request, String prompt) {
            asked.add(prompt);
            // The question is left open before it is told to be asked: the test may end the operation as soon as it
            // is, which withdraws it, and that must complete this one, not one before it.
            if (!answers) {
                CompletableFuture<Option<SecretBuffer>> left = new CompletableFuture<>();
                open = left;
                question.complete(null);
                return left;
            }
            question.complete(null);
            return CompletableFuture.completedFuture(answer.map(text -> {
                SecretBuffer buffer = new SecretBuffer();
                text.codePoints().forEach(buffer::type);
                return buffer;
            }));
        }

        @Override
        public void withdraw(long request) {
            withdrawn.add(request);
            open.complete(Option.none());
        }
    }
}
