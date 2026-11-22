package net.vanfleteren.jazygit.core.forgejo;

import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.list;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.map3;
import static net.vanfleteren.jazygit.lib.json.Decoder.optionalField;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;

import io.vavr.Lazy;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * Forgejo over its HTTP API, with the JDK's client (ADR 174): the token of a host is fj's, sent when fj has one.
 * Which hosts are Forgejo is kept for as long as this lives, which is a repository's opening.
 */
public final class HttpForgejo implements Forgejo {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int OK = 200;
    private static final int SERVER_ERROR = 500;
    // How many pull requests a page of them asks for, and the most pages read: Forgejo gives at most 50 by default.
    private static final int PAGE = 50;
    private static final int PAGES = 10;

    private static final Decoder<CommitStatus.Status> STATUS = map3(
            optionalField("context", string()).map(context -> context.getOrElse("")),
            field("status", string()).map(CommitStatus.State::named),
            optionalField("description", string()).map(description -> description.getOrElse("")),
            CommitStatus.Status::new);

    private static final Decoder<CommitStatus> COMBINED = map2(
            optionalField("state", string()).map(state -> CommitStatus.State.named(state.getOrElse(""))),
            optionalField("statuses", list(STATUS)).map(statuses -> statuses.getOrElse(List.empty())),
            CommitStatus::new);

    private static final Decoder<String> VERSION = field("version", string());

    private final Supplier<FjKeys> keys;
    private final Predicate<ForgejoRepository> trusted;
    private final Lazy<HttpClient> client;
    private final ConcurrentMap<String, Boolean> forgejo = new ConcurrentHashMap<>();

    private HttpForgejo(Supplier<FjKeys> keys, Predicate<ForgejoRepository> trusted) {
        this.keys = Lazy.of(keys);
        this.trusted = trusted;
        this.client = Lazy.of(() -> HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /**
     * Forgejo with fj's keys from {@code keys}, read once, when first needed. A token is sent only over https: a
     * remote over plain HTTP is asked without one, which is enough for a public repository.
     */
    public static HttpForgejo of(Supplier<FjKeys> keys) {
        return new HttpForgejo(keys, ForgejoRepository::secure);
    }

    /**
     * As {@link #of}, but sending the token to the hosts {@code trusted} picks, for a test whose server is plain HTTP.
     */
    static HttpForgejo trusting(Supplier<FjKeys> keys, Predicate<ForgejoRepository> trusted) {
        return new HttpForgejo(keys, trusted);
    }

    @Override
    public Option<ForgejoRepository> repository(String remoteUrl) {
        return ForgejoRepository.of(remoteUrl, keys.get().aliases());
    }

    /**
     * {@inheritDoc} What the host answered is kept; a host that could not be reached, as one behind a VPN that is not
     * up yet, or whose server failed, is asked again the next time.
     */
    @Override
    public boolean isForgejo(ForgejoRepository repository) {
        return tells(repository).getOrElse(false);
    }

    /**
     * {@inheritDoc} A host that could not be reached, or whose server failed, cannot tell.
     */
    @Override
    public Option<Boolean> tells(ForgejoRepository repository) {
        Boolean known = forgejo.get(repository.base());
        if (known != null) {
            return Option.of(known);
        }
        Either<ForgejoError, String> version = get(repository, "/api/forgejo/v1/version")
                .flatMap(body -> VERSION.parse(body).mapLeft(error -> new ForgejoError.Unreadable(error.toString())));
        if (version.isLeft() && passing(version.getLeft())) {
            return Option.none();
        }
        forgejo.put(repository.base(), version.isRight());
        return Option.of(version.isRight());
    }

    /**
     * Whether {@code error} may pass, so that what it says of the host is not kept: the host could not be reached, or
     * its server failed (5xx), as a reverse proxy does while Forgejo restarts. A 4xx, as the 404 of a host that is not
     * Forgejo, is the host's answer.
     */
    private static boolean passing(ForgejoError error) {
        return switch (error) {
            case ForgejoError.Unreachable unreachable -> true;
            case ForgejoError.Refused(int status, String why) -> status >= SERVER_ERROR;
            case ForgejoError.Unreadable unreadable -> false;
        };
    }

    @Override
    public Either<ForgejoError, CommitStatus> status(ForgejoRepository repository, Sha sha) {
        String path = "/api/v1/repos/" + encoded(repository.owner()) + "/" + encoded(repository.name()) + "/commits/"
                + encoded(sha.value()) + "/status";
        return get(repository, path)
                .flatMap(body -> COMBINED.parse(body).mapLeft(error -> new ForgejoError.Unreadable(error.toString())));
    }

    /**
     * {@inheritDoc} They are read a page of {@value #PAGE} at a time, until a page is not full, and at most
     * {@value #PAGES} pages.
     */
    @Override
    public Either<ForgejoError, List<ForgejoPullRequest>> openPullRequests(ForgejoRepository repository) {
        return page(repository, 1, List.empty());
    }

    private Either<ForgejoError, List<ForgejoPullRequest>> page(
            ForgejoRepository repository, int page, List<ForgejoPullRequest> before) {
        return get(repository, repo(repository) + "/pulls?state=open&limit=" + PAGE + "&page=" + page)
                .flatMap(body -> decoded(PullRequestJson.summaries(), body))
                .flatMap(read -> read.size() < PAGE || page >= PAGES
                        ? Either.right(before.appendAll(read))
                        : page(repository, page + 1, before.appendAll(read)));
    }

    @Override
    public Either<ForgejoError, ForgejoPullRequestDetails> pullRequest(ForgejoRepository repository, long number) {
        return get(repository, repo(repository) + "/pulls/" + number)
                .flatMap(body -> decoded(PullRequestJson.details(), body));
    }

    @Override
    public Either<ForgejoError, List<ForgejoReview>> reviews(ForgejoRepository repository, long number) {
        return get(repository, repo(repository) + "/pulls/" + number + "/reviews?limit=" + PAGE)
                .flatMap(body -> decoded(PullRequestJson.reviews(), body));
    }

    private static String repo(ForgejoRepository repository) {
        return "/api/v1/repos/" + encoded(repository.owner()) + "/" + encoded(repository.name());
    }

    private static <T> Either<ForgejoError, T> decoded(Decoder<T> decoder, String body) {
        return decoder.parse(body).mapLeft(error -> new ForgejoError.Unreadable(error.toString()));
    }

    /**
     * The body of what the host of {@code repository} answers to a GET of {@code path}, when that is 200.
     */
    private Either<ForgejoError, String> get(ForgejoRepository repository, String path) {
        return Try.of(() -> {
                    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(repository.base() + path))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Accept", "application/json")
                            .GET();
                    keys.get()
                            .token(repository.host())
                            .filter(token -> trusted.test(repository))
                            .forEach(token -> request.header("Authorization", "token " + token));
                    return client.get()
                            .send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                })
                .toEither()
                .<ForgejoError>mapLeft(failed -> new ForgejoError.Unreachable(String.valueOf(failed.getMessage())))
                .flatMap(response -> response.statusCode() == OK
                        ? Either.right(response.body())
                        : Either.left(new ForgejoError.Refused(response.statusCode(), response.body())));
    }

    private static String encoded(String part) {
        return URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
