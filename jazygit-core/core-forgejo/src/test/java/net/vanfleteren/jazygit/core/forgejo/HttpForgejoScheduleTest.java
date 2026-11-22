package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Merging a pull request when its checks succeed on a Forgejo of the test's: what its base branch requires, its
 * timeline, scheduling and cancelling (ADR 181).
 */
class HttpForgejoScheduleTest {

    private static final Sha HEAD = new Sha("2".repeat(40));

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> asked = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/repos/cvf/nix/pulls/12/merge", exchange -> {
            asked.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            answer(exchange, exchange.getRequestMethod().equals("DELETE") ? 204 : 201, "");
        });
        server.createContext("/api/v1/repos/cvf/gone/pulls/12/merge", exchange -> answer(exchange, 404, ""));
        server.createContext("/api/v1/repos/cvf/nix/branches/main", exchange -> answer(exchange, 200, """
                {"name":"main","protected":true,"enable_status_check":true,
                 "status_check_contexts":["ci / *"],"required_approvals":1,"user_can_merge":true}"""));
        server.createContext("/api/v1/repos/cvf/nix/branches/open", exchange -> answer(exchange, 200, """
                {"name":"open","protected":false,"status_check_contexts":null}"""));
        server.createContext("/api/v1/repos/cvf/nix/issues/12/timeline", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            answer(
                    exchange,
                    200,
                    query.contains("page=1&") || query.endsWith("page=1")
                            ? """
                            [{"type":"comment","user":{"login":"bob"},"created_at":"2026-11-22T09:00:00Z"},
                             {"type":"pull_scheduled_merge","user":{"login":"anna"},"created_at":"2026-11-22T09:10:00Z"},
                             {"type":"pull_cancel_scheduled_merge","user":{"login":"anna"},"created_at":"2026-11-22T09:20:00Z"}]"""
                            : query.contains("page=2") ? """
                                    [{"type":"pull_scheduled_merge","user":{"login":"carl"},"created_at":"2026-11-22T09:30:00Z"},
                                     {"type":"review","user":{"login":"bob"},"created_at":"2026-11-22T09:40:00Z"}]""" : "[]");
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
    void aMergeWhenTheChecksSucceedIsAskedForSoAndAnswered201Scheduled() {
        assertEquals(
                Either.right(new ForgejoMerged.Scheduled()),
                forgejo().merge(repository("nix"), 12, new ForgejoMerge(ForgejoMergeStyle.SQUASH, HEAD, false, true)));
        assertEquals(
                List.of("POST {\"Do\":\"squash\",\"head_commit_id\":\"" + HEAD.value()
                        + "\",\"delete_branch_after_merge\":false,\"merge_when_checks_succeed\":true}"),
                List.ofAll(asked));
    }

    @Test
    void whatTheBaseBranchRequiresIsRead() {
        assertEquals(
                Either.right(new ForgejoBranchRules(true, true, List.of("ci / *"), 1)),
                forgejo().branchRules(repository("nix"), "main"));
        assertEquals(Either.right(ForgejoBranchRules.NONE), forgejo().branchRules(repository("nix"), "open"));
    }

    @Test
    void theMergeScheduledIsTheLastOfTheTimelineOverItsPages() {
        assertEquals(
                Either.right(Option.of(new ForgejoScheduledMerge("carl", Instant.parse("2026-11-22T09:30:00Z")))),
                forgejo().scheduledMerge(repository("nix"), 12));
    }

    @Test
    void cancellingIsADeleteAnsweredWith204AndOneNoLongerScheduledWith404() {
        assertEquals(Option.none(), forgejo().cancelScheduledMerge(repository("nix"), 12));
        assertEquals(List.of("DELETE "), List.ofAll(asked));
        assertEquals(
                Option.of(404),
                forgejo()
                        .cancelScheduledMerge(repository("gone"), 12)
                        .map(error -> ((ForgejoError.Refused) error).status()));
    }
}
