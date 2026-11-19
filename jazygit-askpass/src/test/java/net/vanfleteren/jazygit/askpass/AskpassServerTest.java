package net.vanfleteren.jazygit.askpass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
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
import java.util.function.Function;
import net.vanfleteren.jazygit.process.SshAsking;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        assertEquals(Tuple.of(1, ""), waiting.get());
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
        assertTrue(
                environment.get(AskpassProtocol.VARIABLE).exists(address -> address.endsWith(" " + server.socket())));
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
        Path directory = server.socket().getParent();

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
        server.close();
        assertFalse(Files.exists(directory));
    }

    private void start(boolean asks) {
        server = AskpassServer.start(temporary, directory -> Try.success(Path.of("/the/helper")), (d, ssh) -> asks)
                .get();
        server.answeredBy(user);
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
        private CompletableFuture<Option<SecretBuffer>> open = new CompletableFuture<>();

        void answer(String typed) {
            answer = Option.of(typed);
            answers = true;
        }

        void cancel() {
            answers = true;
        }

        void waitForAQuestion() {
            question.join();
        }

        @Override
        public CompletableFuture<Option<SecretBuffer>> ask(long request, String prompt) {
            asked.add(prompt);
            question.complete(null);
            if (!answers) {
                open = new CompletableFuture<>();
                return open;
            }
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
