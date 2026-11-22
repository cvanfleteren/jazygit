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
import java.util.concurrent.ConcurrentLinkedQueue;
import net.vanfleteren.jazygit.lib.json.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Creating a pull request on a Forgejo of the test's (ADR 178).
 */
class HttpForgejoCreateTest {

    private static final ForgejoNewPullRequest NEW =
            new ForgejoNewPullRequest("anna:feature", "main", "Create it", "Why \"it\"", true);

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> asked = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/repos/cvf/nix/pulls", exchange -> {
            asked.add(exchange.getRequestMethod() + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type") + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            answer(exchange, 201, """
                    {"number":13,"title":"WIP: Create it","draft":true,"html_url":"https://code.example.net/cvf/nix/pulls/13",
                     "head":{"ref":"feature","repo":{"full_name":"anna/nix"}},"base":{"ref":"main"}}
                    """);
        });
        server.createContext("/api/v1/repos/cvf/taken/pulls", exchange -> answer(exchange, 409, """
                {"message":"pull request already exists for these targets [id: 81, issue_id: 7, head_repo_id: 2, \
                base_repo_id: 2, head_branch: feature, base_branch: main]","url":"https://code.example.net/api/swagger"}
                """));
        server.createContext("/api/v1/repos/cvf/locked/pulls", exchange -> answer(exchange, 401, """
                {"message":"token is required","url":"https://code.example.net/api/swagger"}"""));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private ForgejoRepository repository(String name) {
        return new ForgejoRepository("http://127.0.0.1:" + server.getAddress().getPort(), "cvf", name);
    }

    @Test
    void itIsPostedAsJsonWithTheTokenAndAnsweredWithThePullRequest() {
        ForgejoRepository nix = repository("nix");
        Forgejo forgejo = HttpForgejo.of(() -> new FjKeys(HashMap.of(nix.host(), "secret"), HashMap.empty()));

        Either<ForgejoError, ForgejoPullRequest> created = forgejo.createPullRequest(nix, NEW);

        assertEquals(
                Either.right(new ForgejoPullRequest(
                        13,
                        "WIP: Create it",
                        "feature",
                        Option.of("anna/nix"),
                        "main",
                        true,
                        "https://code.example.net/cvf/nix/pulls/13")),
                created);
        String sent = List.ofAll(asked).single();
        assertEquals("POST application/json token secret ", sent.substring(0, sent.indexOf('{')));
        assertEquals(
                Json.parse("{\"head\":\"anna:feature\",\"base\":\"main\",\"title\":\"WIP: Create it\","
                        + "\"body\":\"Why \\\"it\\\"\"}"),
                Json.parse(sent.substring(sent.indexOf('{'))));
    }

    @Test
    void aPullRequestOpenAlreadyIsRefusedNamingIt() {
        ForgejoError refused = HttpForgejo.of(() -> FjKeys.NONE)
                .createPullRequest(repository("taken"), NEW)
                .getLeft();

        assertEquals(Option.of(7L), ((ForgejoError.Refused) refused).existing());
    }

    @Test
    void withoutATokenItIsRefusedWithForgejosMessage() {
        ForgejoError refused = HttpForgejo.of(() -> FjKeys.NONE)
                .createPullRequest(repository("locked"), NEW)
                .getLeft();

        assertEquals(401, ((ForgejoError.Refused) refused).status());
        assertEquals("token is required", ((ForgejoError.Refused) refused).message());
        assertEquals(Option.none(), ((ForgejoError.Refused) refused).existing());
    }
}
