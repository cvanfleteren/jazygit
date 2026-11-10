package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import net.vanfleteren.jazygit.gh.model.Verdict;

/**
 * Approving a pull request, or asking for changes (ADR 99), with GraphQL's {@code addPullRequestReview}, as
 * {@code gh pr review} does: one call that adds a review and submits it. Not {@code gh pr review} itself, so that a
 * pending review can later be submitted here with {@code submitPullRequestReview} instead. The text is passed as a
 * string ({@code -f}), never as {@code -F}, which reads a value starting with {@code @} from a file.
 */
final class ReviewMutation {

    static final String SUBMIT = """
            mutation($pullRequest: ID!, $event: PullRequestReviewEvent!, $body: String!) { \
            addPullRequestReview(input: { pullRequestId: $pullRequest, event: $event, body: $body }) { \
            pullRequestReview { state } } }""";

    private ReviewMutation() {}

    /**
     * The command that submits a review saying {@code verdict}, with {@code body}, on the pull request with node id
     * {@code pullRequest}.
     */
    static GhCommand submit(String pullRequest, Verdict verdict, String body) {
        return gh(
                "api",
                "graphql",
                "-f",
                "query=" + SUBMIT,
                "-f",
                "pullRequest=" + pullRequest,
                "-f",
                "event=" + verdict.name(),
                "-f",
                "body=" + body);
    }
}
