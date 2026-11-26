package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Merging a pull request on a Forgejo of the test's, and reading how its repository lets it be merged (ADR 180).
 */
class HttpForgejoMergeTest {

    private static final Sha HEAD = new Sha("2".repeat(40));
    private static final ForgejoMerge SQUASH = new ForgejoMerge(ForgejoMergeStyle.SQUASH, HEAD, true);

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> asked = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/repos/cvf/nix/pulls/12/merge", exchange -> {
            asked.add(exchange.getRequestMethod() + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type") + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            answer(exchange, 200, "");
        });
        server.createContext("/api/v1/repos/cvf/kept/pulls/12/merge", exchange -> answer(exchange, 403, """
                {"message":"the head branch is protected","url":"https://code.example.net/api/swagger"}"""));
        server.createContext("/api/v1/repos/cvf/forbidden/pulls/12/merge", exchange -> answer(exchange, 403, """
                {"message":"user should have specific permission","url":"https://code.example.net/api/swagger"}"""));
        server.createContext("/api/v1/repos/cvf/conflicting/pulls/12/merge", exchange -> answer(exchange, 409, """
                {"Style":"merge","StdOut":"","StdErr":"CONFLICT (content)","Err":{}}"""));
        server.createContext("/api/v1/repos/cvf/nix", exchange -> answer(exchange, 200, """
                {"name":"nix","allow_merge_commits":true,"allow_rebase":false,"allow_rebase_explicit":true,
                 "allow_squash_merge":true,"allow_fast_forward_only_merge":false,
                 "default_merge_style":"squash","default_delete_branch_after_merge":true}"""));
        server.createContext("/api/v1/repos/cvf/bare", exchange -> answer(exchange, 200, "{\"name\":\"bare\"}"));
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

    @Test
    void itIsPostedAsJsonWithTheTokenTheStyleTheHeadAndWhetherToDeleteTheBranch() {
        ForgejoRepository nix = repository("nix");
        Forgejo forgejo = HttpForgejo.of(() -> new FjKeys(HashMap.of(nix.host(), "secret"), HashMap.empty()));

        assertEquals(Either.right(new ForgejoMerged.Merged()), forgejo.merge(nix, new PrNumber(12), SQUASH));
        assertEquals(
                List.of("POST application/json token secret {\"Do\":\"squash\",\"head_commit_id\":\"" + HEAD.value()
                        + "\",\"delete_branch_after_merge\":true}"),
                List.ofAll(asked));
    }

    @Test
    void aBranchThatCouldNotBeDeletedIsMergedWithItsBranchKept() {
        assertEquals(
                Either.right(new ForgejoMerged.BranchKept("the head branch is protected")),
                HttpForgejo.of(() -> FjKeys.NONE).merge(repository("kept"), new PrNumber(12), SQUASH));
    }

    @Test
    void anyOther403OrAConflictIsRefusedWithWhatForgejoAnswered() {
        Forgejo forgejo = HttpForgejo.of(() -> FjKeys.NONE);

        ForgejoError forbidden =
                forgejo.merge(repository("forbidden"), new PrNumber(12), SQUASH).getLeft();
        assertEquals(Option.of("user should have specific permission"), ((ForgejoError.Refused) forbidden).stated());

        ForgejoError.Refused conflict =
                (ForgejoError.Refused) forgejo.merge(repository("conflicting"), new PrNumber(12), SQUASH)
                        .getLeft();
        assertEquals(409, conflict.status());
        assertEquals(Option.none(), conflict.stated());
    }

    @Test
    void theStylesTheRepositoryAllowsAndWhetherItDeletesTheBranchAreRead() {
        Forgejo forgejo = HttpForgejo.of(() -> FjKeys.NONE);

        assertEquals(
                Either.right(new ForgejoMergeSettings(
                        LinkedHashSet.of(
                                ForgejoMergeStyle.MERGE, ForgejoMergeStyle.SQUASH, ForgejoMergeStyle.REBASE_MERGE),
                        true)),
                forgejo.mergeSettings(repository("nix")));
        assertEquals(
                Either.right(new ForgejoMergeSettings(LinkedHashSet.empty(), false)),
                forgejo.mergeSettings(repository("bare")));
    }
}
