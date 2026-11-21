package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Forgejo's API as a local server answers it, with what code.vanfleteren.net answered.
 */
class HttpForgejoTest {

    private static final Sha SHA = new Sha("e10f9db854e2d362ffc5f5c6922c883791b98dcd");

    private static final String STATUS = """
            {"state":"failure","sha":"e10f9db854e2d362ffc5f5c6922c883791b98dcd","total_count":1,
             "statuses":[{"id":3,"status":"failure","target_url":"/cvf/nix/actions/runs/6/jobs/0",
                          "description":"Failing after 11m2s","context":"check / check (push)","creator":null}],
             "repository":{"id":1}}
            """;

    private static final String NO_STATUS = """
            {"state":"","sha":"","total_count":0,"statuses":null,"repository":null,"commit_url":"","url":""}
            """;

    private HttpServer server;
    private final AtomicInteger versionAsked = new AtomicInteger();
    private final AtomicInteger notFound = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> authorizations = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/forgejo/v1/version", exchange -> {
            versionAsked.incrementAndGet();
            answer(exchange, 200, "{\"version\":\"17.0.2+gitea-1.22.0\"}");
        });
        server.createContext("/api/v1/repos/cvf/nix/commits/" + SHA.value() + "/status", exchange -> {
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            answer(exchange, 200, STATUS);
        });
        server.createContext(
                "/api/v1/repos/cvf/empty/commits/" + SHA.value() + "/status",
                exchange -> answer(exchange, 200, NO_STATUS));
        server.createContext("/", exchange -> {
            notFound.incrementAndGet();
            answer(exchange, 404, "{\"message\":\"The target couldn't be found.\"}");
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void answer(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private ForgejoRepository repository(String name) {
        return new ForgejoRepository(base(), "cvf", name);
    }

    @Test
    void aHostThatAnswersForgejosVersionIsForgejoAndIsAskedOnce() {
        HttpForgejo forgejo = HttpForgejo.of(() -> FjKeys.NONE);
        assertTrue(forgejo.isForgejo(repository("nix")));
        assertTrue(forgejo.isForgejo(repository("other")));
        assertEquals(1, versionAsked.get());
    }

    @Test
    void aHostThatDoesNotAnswerItIsNotAndIsAskedOnce() {
        server.removeContext("/api/forgejo/v1/version");
        HttpForgejo forgejo = HttpForgejo.of(() -> FjKeys.NONE);
        assertFalse(forgejo.isForgejo(repository("nix")));
        // Its answer stands: it is not asked again.
        assertFalse(forgejo.isForgejo(repository("nix")));
        assertEquals(1, notFound.get());
    }

    @Test
    void aHostThatCouldNotBeReachedIsAskedAgainOnceItCanBe() throws IOException {
        // A port nothing listens on yet, as a host behind a VPN that is not up.
        int port;
        try (ServerSocket free = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = free.getLocalPort();
        }
        ForgejoRepository later = new ForgejoRepository("http://127.0.0.1:" + port, "cvf", "nix");
        HttpForgejo forgejo = HttpForgejo.of(() -> FjKeys.NONE);
        assertFalse(forgejo.isForgejo(later));

        HttpServer up = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        up.createContext("/api/forgejo/v1/version", exchange -> answer(exchange, 200, "{\"version\":\"17.0.2\"}"));
        up.start();
        try {
            assertTrue(forgejo.isForgejo(later));
        } finally {
            up.stop(0);
        }
    }

    @Test
    void theStatusOfACommitIsReadWithFjsTokenForTheHost() {
        String host = base().substring("http://".length());
        // This server is plain HTTP; it stands for one over https, where the token is sent.
        HttpForgejo forgejo =
                HttpForgejo.trusting(() -> new FjKeys(HashMap.of(host, "secret"), HashMap.empty()), repository -> true);
        assertEquals(
                Either.right(new CommitStatus(
                        CommitStatus.State.FAILED,
                        List.of(new CommitStatus.Status(
                                "check / check (push)", CommitStatus.State.FAILED, "Failing after 11m2s")))),
                forgejo.status(repository("nix"), SHA));
        assertEquals(List.of("token secret"), List.ofAll(authorizations));
    }

    @Test
    void fjsTokenIsNotSentOverPlainHttp() {
        String host = base().substring("http://".length());
        HttpForgejo.of(() -> new FjKeys(HashMap.of(host, "secret"), HashMap.empty()))
                .status(repository("nix"), SHA);
        assertEquals(List.of("null"), List.ofAll(authorizations));
    }

    @Test
    void withoutATokenItAsksWithoutOne() {
        HttpForgejo.of(() -> FjKeys.NONE).status(repository("nix"), SHA);
        assertEquals(List.of("null"), List.ofAll(authorizations));
    }

    @Test
    void aCommitWithoutStatusesHasNone() {
        assertEquals(
                Either.right(CommitStatus.NONE),
                HttpForgejo.of(() -> FjKeys.NONE).status(repository("empty"), SHA));
    }

    @Test
    void aRepositoryTheHostDoesNotShowIsRefused() {
        Either<ForgejoError, CommitStatus> status =
                HttpForgejo.of(() -> FjKeys.NONE).status(repository("private"), SHA);
        assertEquals(404, ((ForgejoError.Refused) status.getLeft()).status());
    }

    @Test
    void aHostThatIsNotThereIsUnreachable() {
        String base = base();
        server.stop(0);
        Either<ForgejoError, CommitStatus> status =
                HttpForgejo.of(() -> FjKeys.NONE).status(new ForgejoRepository(base, "cvf", "nix"), SHA);
        assertInstanceOf(ForgejoError.Unreachable.class, status.getLeft());
    }
}
