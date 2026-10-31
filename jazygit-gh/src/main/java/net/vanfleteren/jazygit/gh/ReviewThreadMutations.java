package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;

/**
 * Starting a review thread on a pull request, replying to one, resolving it and deleting a comment of it (ADR 60,
 * ADR 64). A thread is started and
 * replied to with REST's review comments, which GitHub publishes at once, as its "Add single comment" does: GraphQL's
 * {@code addPullRequestReviewThread} and {@code addPullRequestReviewThreadReply} add them to the viewer's pending
 * review instead, which only they see until it is submitted. A thread is resolved with GraphQL, which REST cannot,
 * and a comment deleted with GraphQL too, by the node id it is read with.
 * The text of a comment and a path are passed as strings ({@code -f}), never as {@code -F}, which reads a value
 * starting with {@code @} from a file. gh fills in the repository of the working tree for {owner} and {repo}.
 */
final class ReviewThreadMutations {

    static final String RESOLVE = """
            mutation($thread: ID!) { resolveReviewThread(input: { threadId: $thread }) { thread { id } } }""";

    static final String UNRESOLVE = """
            mutation($thread: ID!) { unresolveReviewThread(input: { threadId: $thread }) { thread { id } } }""";

    static final String DELETE = """
            mutation($comment: ID!) { deletePullRequestReviewComment(input: { id: $comment }) { clientMutationId } }""";

    private ReviewThreadMutations() {}

    /**
     * The command that starts {@code thread} on pull request {@code number}, at its head commit {@code commit}: on its
     * lines, the first only when it has several, or on its whole file.
     */
    static GhCommand start(int number, String commit, NewReviewThread thread) {
        List<String> place = thread.lines()
                .map(lines -> List.of("-F", "line=" + lines.line(), "-f", "side=" + lines.side())
                        .appendAll(
                                lines.single()
                                        ? List.empty()
                                        : List.of(
                                                "-F",
                                                "start_line=" + lines.startLine(),
                                                "-f",
                                                "start_side=" + lines.startSide())))
                .getOrElse(() -> List.of("-f", "subject_type=file"));
        return post(
                "repos/{owner}/{repo}/pulls/" + number + "/comments",
                List.of("-f", "body=" + thread.body(), "-f", "commit_id=" + commit, "-f", "path=" + thread.path())
                        .appendAll(place));
    }

    /**
     * The command that replies {@code body} to the thread of pull request {@code number} whose first comment has the
     * id {@code comment}, as REST numbers it.
     */
    static GhCommand reply(int number, String comment, String body) {
        return post(
                "repos/{owner}/{repo}/pulls/" + number + "/comments/" + comment + "/replies",
                List.of("-f", "body=" + body));
    }

    /**
     * The command that resolves the thread with node id {@code thread} when {@code resolved}, else unresolves it.
     */
    static GhCommand resolve(String thread, boolean resolved) {
        return gh(List.of("api", "graphql", "-f", "query=" + (resolved ? RESOLVE : UNRESOLVE), "-f", "thread=" + thread)
                .toJavaArray(String[]::new));
    }

    /**
     * The command that deletes the comment of a review thread with node id {@code comment}.
     */
    static GhCommand delete(String comment) {
        return gh(List.of("api", "graphql", "-f", "query=" + DELETE, "-f", "comment=" + comment)
                .toJavaArray(String[]::new));
    }

    private static GhCommand post(String path, List<String> fields) {
        return gh(List.of("api", "--method", "POST", path).appendAll(fields).toJavaArray(String[]::new));
    }
}
