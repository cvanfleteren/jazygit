package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Activity;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Branches;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Conversation;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Size;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.State;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Status;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview.Verdict;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The pull requests of a repository and their reviews, as a local server answers them in the shape of Forgejo's API
 * (ADR 176).
 */
class HttpForgejoPullRequestsTest {

    private static final String SHA = "e10f9db854e2d362ffc5f5c6922c883791b98dcd";

    private static final String DETAILS = """
            {"id":7,"number":12,"title":"Show the pull request","body":"Why",
             "user":{"id":1,"login":"cvf"},"labels":[{"id":3,"name":"feature"},{"id":4,"name":"ui"}],
             "state":"open","draft":true,"is_locked":false,"mergeable":true,"merged":false,"merged_at":null,
             "comments":4,"review_comments":2,"additions":120,"deletions":7,"changed_files":5,
             "html_url":"https://code.example.net/cvf/nix/pulls/12",
             "head":{"label":"feature","ref":"feature","sha":"%s","repo_id":1,
                     "repo":{"id":1,"full_name":"cvf/nix"}},
             "base":{"label":"main","ref":"main","sha":"0000000000000000000000000000000000000000","repo_id":1},
             "requested_reviewers":[{"id":2,"login":"anna"}],"requested_reviewers_teams":[],
             "created_at":"2026-11-20T09:00:00+01:00","updated_at":"2026-11-21T10:30:00Z","closed_at":null}
            """.formatted(SHA);

    private static final String REVIEWS = """
            [{"id":1,"user":{"login":"anna"},"state":"REQUEST_CHANGES","stale":true,"dismissed":false},
             {"id":2,"user":{"login":"anna"},"state":"APPROVED","stale":false,"dismissed":false},
             {"id":3,"user":{"login":"bob"},"state":"COMMENT","stale":false,"dismissed":false},
             {"id":4,"user":null,"state":"PENDING"}]
            """;

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> pages = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/api/v1/repos/cvf/nix/pulls/12/reviews",
                exchange -> answer(exchange, 200, page(exchange) == 1 ? REVIEWS : "[]"));
        server.createContext("/api/v1/repos/cvf/nix/pulls/12", exchange -> answer(exchange, 200, DETAILS));
        server.createContext("/api/v1/repos/cvf/long/pulls/1/reviews", exchange -> {
            // 50 comments on the first page, then Anna asks for changes on the second, and nothing after.
            int page = page(exchange);
            answer(
                    exchange,
                    200,
                    page == 1
                            ? List.range(0, 50)
                                    .map(i -> "{\"user\":{\"login\":\"bob\"},\"state\":\"COMMENT\"}")
                                    .mkString("[", ",", "]")
                            : page == 2 ? "[{\"user\":{\"login\":\"anna\"},\"state\":\"REQUEST_CHANGES\"}]" : "[]");
        });
        server.createContext("/api/v1/repos/cvf/nix/pulls", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            pages.add(query);
            // A full first page of 50, then 2 on the second, and nothing after.
            int page = page(exchange);
            int count = page == 1 ? 50 : page == 2 ? 2 : 0;
            int first = page == 1 ? 1 : 51;
            answer(
                    exchange,
                    200,
                    List.range(first, first + count)
                            .map(HttpForgejoPullRequestsTest::summary)
                            .mkString("[", ",", "]"));
        });
        server.createContext("/api/v1/repos/cvf/small/pulls", exchange -> {
            // A host that serves at most 30 a page, of 40 open pull requests.
            int first = (page(exchange) - 1) * 30 + 1;
            answer(
                    exchange,
                    200,
                    List.range(first, Math.min(first + 30, 41))
                            .map(HttpForgejoPullRequestsTest::summary)
                            .mkString("[", ",", "]"));
        });
        server.createContext("/api/v1/repos/cvf/forked/pulls", exchange -> answer(exchange, 200, """
                [{"number":3,"title":"From a fork","draft":false,"html_url":"https://code.example.net/cvf/forked/pulls/3",
                  "head":{"ref":"main","repo":{"full_name":"someone/forked"}},"base":{"ref":"main"}},
                 {"number":4,"title":"From a deleted fork","head":{"ref":"gone","repo":null},"base":{"ref":"main"}}]
                """));
        server.createContext("/", exchange -> answer(exchange, 404, "{\"message\":\"The target couldn't be found.\"}"));
        server.start();
    }

    private static int page(HttpExchange exchange) {
        return List.of(exchange.getRequestURI().getQuery().split("&"))
                .find(part -> part.startsWith("page="))
                .map(part -> Integer.parseInt(part.substring("page=".length())))
                .getOrElse(1);
    }

    private static String summary(int number) {
        return """
                {"number":%d,"title":"Pull request %d","draft":false,"html_url":"https://code.example.net/cvf/nix/pulls/%d",
                 "head":{"ref":"branch-%d","repo":{"full_name":"cvf/nix"}},"base":{"ref":"main"}}
                """.formatted(number, number, number, number);
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
    void theOpenPullRequestsAreReadAPageAtATimeUntilOneIsNotFull() {
        List<ForgejoPullRequest> open = HttpForgejo.of(() -> FjKeys.NONE)
                .openPullRequests(repository("nix"))
                .get();

        assertEquals(52, open.size());
        assertEquals(
                new ForgejoPullRequest(
                        51,
                        "Pull request 51",
                        "branch-51",
                        Option.of("cvf/nix"),
                        "main",
                        false,
                        "https://code.example.net/cvf/nix/pulls/51"),
                open.get(50));
        assertEquals(
                List.of("state=open&limit=50&page=1", "state=open&limit=50&page=2", "state=open&limit=50&page=3"),
                List.ofAll(pages));
    }

    @Test
    void aHostThatServesFewerAPageIsReadUntilAPageIsEmpty() {
        List<ForgejoPullRequest> open = HttpForgejo.of(() -> FjKeys.NONE)
                .openPullRequests(repository("small"))
                .get();

        assertEquals(List.rangeClosed(1L, 40L), open.map(ForgejoPullRequest::number));
    }

    @Test
    void aPullRequestFromAForkSaysWhichAndOneFromADeletedForkNone() {
        List<ForgejoPullRequest> open = HttpForgejo.of(() -> FjKeys.NONE)
                .openPullRequests(repository("forked"))
                .get();

        assertEquals(Option.of("someone/forked"), open.get(0).headRepository());
        assertEquals(Option.none(), open.get(1).headRepository());
    }

    @Test
    void aPullRequestIsReadAsItIsShown() {
        assertEquals(
                new ForgejoPullRequestDetails(
                        12,
                        "Show the pull request",
                        "cvf",
                        new Status(State.OPEN, true, true),
                        new Branches("feature", "main", new Sha(SHA)),
                        new Activity(Instant.parse("2026-11-20T08:00:00Z"), Instant.parse("2026-11-21T10:30:00Z")),
                        new Size(120, 7, 5),
                        new Conversation(4, List.of("feature", "ui"), List.of("anna")),
                        "https://code.example.net/cvf/nix/pulls/12"),
                HttpForgejo.of(() -> FjKeys.NONE)
                        .pullRequest(repository("nix"), 12)
                        .get());
    }

    @Test
    void theReviewsOfAPullRequestAreReadInTheOrderTheyWereGiven() {
        List<ForgejoReview> reviews =
                HttpForgejo.of(() -> FjKeys.NONE).reviews(repository("nix"), 12).get();

        assertEquals(
                List.of(
                        new ForgejoReview("anna", Verdict.CHANGES_REQUESTED, true, false),
                        new ForgejoReview("anna", Verdict.APPROVED, false, false),
                        new ForgejoReview("bob", Verdict.COMMENTED, false, false),
                        new ForgejoReview("", Verdict.PENDING, false, false)),
                reviews);
        // Anna approved after asking for changes; Bob only commented.
        assertEquals(Option.of(Verdict.APPROVED), ForgejoReview.standing(reviews));
    }

    @Test
    void theReviewsAreReadAPageAtATimeSoTheLatestCount() {
        List<ForgejoReview> reviews =
                HttpForgejo.of(() -> FjKeys.NONE).reviews(repository("long"), 1).get();

        assertEquals(51, reviews.size());
        assertEquals(Option.of(Verdict.CHANGES_REQUESTED), ForgejoReview.standing(reviews));
    }

    @Test
    void aPullRequestTheHostDoesNotHaveIsRefused() {
        assertInstanceOf(
                ForgejoError.Refused.class,
                HttpForgejo.of(() -> FjKeys.NONE)
                        .pullRequest(repository("other"), 1)
                        .getLeft());
    }
}
