package net.vanfleteren.jazygit.gh.pullrequests;

import static net.vanfleteren.jazygit.gh.process.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.process.GhCommand;

/**
 * Turning the auto-merge of a pull request on and off (ADR 112), with GraphQL's {@code enablePullRequestAutoMerge} and
 * {@code disablePullRequestAutoMerge}: GitHub then merges it by itself once nothing blocks it any more. Not
 * {@code gh pr merge --auto}, which merges at once a pull request that can be merged now.
 */
final class AutoMergeMutation {

    static final String ENABLE = """
            mutation($pullRequest: ID!, $method: PullRequestMergeMethod!, $head: GitObjectID) { \
            enablePullRequestAutoMerge(input: { pullRequestId: $pullRequest, mergeMethod: $method, \
            expectedHeadOid: $head }) { clientMutationId } }""";

    static final String DISABLE = """
            mutation($pullRequest: ID!) { \
            disablePullRequestAutoMerge(input: { pullRequestId: $pullRequest }) { clientMutationId } }""";

    private AutoMergeMutation() {}

    /**
     * The command that turns auto-merge on for the pull request with node id {@code pullRequest}, merging it as
     * {@code method} says, only while its branch is at {@code head}, if given.
     */
    static GhCommand enable(String pullRequest, MergeMethod method, Option<GhCommit> head) {
        return gh(List.of(
                        "api",
                        "graphql",
                        "-f",
                        "query=" + ENABLE,
                        "-f",
                        "pullRequest=" + pullRequest,
                        "-f",
                        "method=" + method.name())
                .appendAll(head.toList().flatMap(commit -> List.of("-f", "head=" + commit.oid())))
                .toJavaArray(String[]::new));
    }

    /**
     * The command that turns auto-merge off for the pull request with node id {@code pullRequest}.
     */
    static GhCommand disable(String pullRequest) {
        return gh("api", "graphql", "-f", "query=" + DISABLE, "-f", "pullRequest=" + pullRequest);
    }
}
