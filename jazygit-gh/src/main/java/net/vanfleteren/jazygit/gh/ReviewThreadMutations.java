package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * Starting a review thread on a pull request, replying to one and resolving it, with GraphQL's mutations (ADR 60).
 * The text of a comment and a path are passed as strings ({@code -f}), never as {@code -F}, which reads a value
 * starting with {@code @} from a file.
 */
final class ReviewThreadMutations {

    static final String START = """
            mutation($pullRequest: ID!, $path: String!, $body: String!, $line: Int, $side: DiffSide, \
            $startLine: Int, $startSide: DiffSide, $subject: PullRequestReviewThreadSubjectType) { \
            addPullRequestReviewThread(input: { pullRequestId: $pullRequest, path: $path, body: $body, line: $line, \
            side: $side, startLine: $startLine, startSide: $startSide, subjectType: $subject }) { thread { id } } }""";

    static final String REPLY = """
            mutation($thread: ID!, $body: String!) { \
            addPullRequestReviewThreadReply(input: { pullRequestReviewThreadId: $thread, body: $body }) \
            { comment { id } } }""";

    static final String RESOLVE = """
            mutation($thread: ID!) { resolveReviewThread(input: { threadId: $thread }) { thread { id } } }""";

    static final String UNRESOLVE = """
            mutation($thread: ID!) { unresolveReviewThread(input: { threadId: $thread }) { thread { id } } }""";

    // The id of the thread that was started.
    static final Decoder<String> STARTED = at("data.addPullRequestReviewThread.thread.id", string());

    private ReviewThreadMutations() {}

    /**
     * The command that starts {@code thread} on the pull request with node id {@code pullRequest}: on its lines, the
     * first only when it has several, or on its whole file.
     */
    static GhCommand start(String pullRequest, NewReviewThread thread) {
        List<String> place = thread.lines()
                .map(lines -> List.of("-F", "line=" + lines.line(), "-f", "side=" + lines.side())
                        .appendAll(
                                lines.single()
                                        ? List.empty()
                                        : List.of(
                                                "-F",
                                                "startLine=" + lines.startLine(),
                                                "-f",
                                                "startSide=" + lines.startSide()))
                        .appendAll(List.of("-f", "subject=LINE")))
                .getOrElse(() -> List.of("-f", "subject=FILE"));
        return graphql(
                START,
                List.of(
                                "-f",
                                "pullRequest=" + pullRequest,
                                "-f",
                                "path=" + thread.path(),
                                "-f",
                                "body=" + thread.body())
                        .appendAll(place));
    }

    /**
     * The command that replies {@code body} to the thread with node id {@code thread}.
     */
    static GhCommand reply(String thread, String body) {
        return graphql(REPLY, List.of("-f", "thread=" + thread, "-f", "body=" + body));
    }

    /**
     * The command that resolves the thread with node id {@code thread} when {@code resolved}, else unresolves it.
     */
    static GhCommand resolve(String thread, boolean resolved) {
        return graphql(resolved ? RESOLVE : UNRESOLVE, List.of("-f", "thread=" + thread));
    }

    private static GhCommand graphql(String query, List<String> variables) {
        return gh(List.of("api", "graphql", "-f", "query=" + query)
                .appendAll(variables)
                .toJavaArray(String[]::new));
    }
}
