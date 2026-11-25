package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The checks of a commit, the log of a job and running it again, on a Forgejo of the test's (ADR 195).
 */
class HttpForgejoChecksTest {

    private static final Sha SHA = new Sha("3".repeat(40));
    private static final ForgejoCheck.ActionsJob JOB = new ForgejoCheck.ActionsJob(12, 1);

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> asked = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/repos/cvf/nix/commits/" + SHA.value() + "/statuses", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            answer(exchange, 200, query.contains("page=1") && !query.contains("page=10") ? """
                    [{"id":2,"context":"ci / test (push)","status":"failure","description":"Failing after 5s",
                      "target_url":"https://x/cvf/nix/actions/runs/12/jobs/1","created_at":"2026-11-25T10:00:10Z"},
                     {"id":1,"context":"ci / test (push)","status":"pending","description":"Has started running",
                      "target_url":"https://x/cvf/nix/actions/runs/12/jobs/1","created_at":"2026-11-25T10:00:05Z"}]""" : "[]");
        });
        server.createContext("/api/v1/repos/cvf/nix/actions/runs", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/runs")) {
                asked.add("runs " + exchange.getRequestURI().getQuery());
                answer(exchange, 200, """
                        {"total_count":1,"workflow_runs":[{"id":345,"index_in_repo":12}]}""");
            } else {
                answer(exchange, 200, """
                        [{"id":902,"name":"lint"},{"id":901,"name":"build"},{"id":903,"name":"test"}]""");
            }
        });
        server.createContext("/api/v1/repos/cvf/nix/actions/jobs/902/logs", exchange -> {
            asked.add("log " + exchange.getRequestHeaders().getFirst("Accept"));
            answer(exchange, 200, "line one\nline two\n");
        });
        server.createContext("/api/v1/repos/cvf/nix/actions/jobs/902/rerun", exchange -> {
            asked.add(exchange.getRequestMethod() + " rerun "
                    + exchange.getRequestHeaders().getFirst("Authorization"));
            answer(exchange, 204, "");
        });
        server.createContext("/api/v1/repos/cvf/old/actions/runs", exchange -> answer(exchange, 404, ""));
        // A Forgejo that does not filter the runs by their number: the newest comes first, whatever was asked.
        server.createContext("/api/v1/repos/cvf/unfiltered/actions/runs", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/runs")) {
                answer(exchange, 200, """
                        {"total_count":2,"workflow_runs":[{"id":346,"index_in_repo":13},{"id":345,"index_in_repo":12}]}""");
            } else {
                answer(exchange, 200, """
                        [{"id":%d}]""".formatted(
                                exchange.getRequestURI().getPath().contains("/345/") ? 902 : 912));
            }
        });
        server.createContext("/api/v1/repos/cvf/unfiltered/actions/jobs", exchange -> {
            asked.add("unfiltered " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI().getPath());
            answer(exchange, exchange.getRequestMethod().equals("POST") ? 204 : 200, "log");
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private ForgejoRepository repository(String name) {
        return new ForgejoRepository("http://127.0.0.1:" + server.getAddress().getPort(), "cvf", name);
    }

    private Forgejo forgejo() {
        return HttpForgejo.of(() -> new FjKeys(HashMap.of(repository("nix").host(), "secret"), HashMap.empty()));
    }

    @Test
    void theChecksAreFoldedFromAllTheStatusesPosted() {
        List<ForgejoCheck> checks = forgejo().checks(repository("nix"), SHA).get();
        assertEquals(List.of("ci / test (push)"), checks.map(ForgejoCheck::name));
        assertEquals(CommitStatus.State.FAILED, checks.head().state());
        assertEquals(Option.of(JOB), checks.head().job());
    }

    @Test
    void theLogOfAJobIsReadByTheIdOfItsPlaceInItsRun() {
        assertEquals(Either.right("line one\nline two\n"), forgejo().jobLog(repository("nix"), JOB));
        assertTrue(asked.contains("runs run_number=12"));
        assertTrue(asked.contains("log text/plain"));
    }

    @Test
    void aJobIsRunAgainWithTheToken() {
        assertEquals(Option.none(), forgejo().rerunJob(repository("nix"), JOB));
        assertTrue(asked.contains("POST rerun token secret"));
    }

    @Test
    void aForgejoWithoutTheActionsApiRefusesIt() {
        assertTrue(forgejo().jobLog(repository("old"), JOB).getLeft() instanceof ForgejoError.Refused refused
                && refused.status() == 404);
    }

    @Test
    void aJobIsFoundInTheRunWithItsNumberThoughTheServerListsOthersFirst() {
        ForgejoCheck.ActionsJob first = new ForgejoCheck.ActionsJob(12, 0);

        assertEquals(Option.none(), forgejo().rerunJob(repository("unfiltered"), first));
        assertTrue(
                asked.contains("unfiltered POST /api/v1/repos/cvf/unfiltered/actions/jobs/902/rerun"), asked::toString);
    }

    @Test
    void aJobOfARunTheServerDoesNotListIsNeitherReadNorRunAgain() {
        ForgejoCheck.ActionsJob gone = new ForgejoCheck.ActionsJob(7, 0);

        assertTrue(forgejo().jobLog(repository("unfiltered"), gone).isLeft());
        assertTrue(forgejo().rerunJob(repository("unfiltered"), gone).isDefined());
        assertTrue(asked.stream().noneMatch(request -> request.startsWith("unfiltered")), asked::toString);
    }

    @Test
    void aJobPastTheJobsOfItsRunIsNotFound() {
        assertTrue(forgejo()
                .jobLog(repository("nix"), new ForgejoCheck.ActionsJob(12, 5))
                .isLeft());
    }
}
