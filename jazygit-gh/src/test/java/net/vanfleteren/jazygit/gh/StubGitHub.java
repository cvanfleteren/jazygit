package net.vanfleteren.jazygit.gh;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Activity;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Branches;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Size;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * A {@link GitHub} for tests: a repository without pull requests or branches, whose operations all succeed without running
 * anything. Fakes override what they need.
 */
public interface StubGitHub extends GitHub {

    /**
     * A GitHub repository without pull requests.
     */
    static StubGitHub empty() {
        return new StubGitHub() {};
    }

    @Override
    default Either<GhError, String> version(String executable) {
        return Either.right("2.102.0");
    }

    /**
     * Logged in to no account.
     */
    @Override
    default Either<GhError, List<GhLogin>> logins() {
        return Either.right(List.empty());
    }

    /**
     * The repository {@code owner/repo} without pull requests.
     */
    @Override
    default Either<GhError, RepositoryPullRequests> repositoryPullRequests() {
        return Either.right(new RepositoryPullRequests("owner/repo", PullRequestList.EMPTY));
    }

    @Override
    default Either<GhError, MyPullRequests> myPullRequests() {
        return Either.right(new MyPullRequests(PullRequestList.EMPTY, PullRequestList.EMPTY));
    }

    /**
     * A pull request into {@code main} without a description or changes.
     */
    @Override
    default Either<GhError, PullRequestPreview> pullRequestPreview(String url) {
        return Either.right(new PullRequestPreview("main", "", "", false));
    }

    /**
     * A repository {@code owner/repo} without branches.
     */
    @Override
    default Either<GhError, BranchChecks> branchChecks() {
        return Either.right(new BranchChecks("owner/repo", HashMap.empty()));
    }

    /**
     * The repository {@code owner/repo} without checks on any branch.
     */
    @Override
    default Either<GhError, BranchCheckRuns> branchCheckRuns(List<String> branches) {
        return Either.right(new BranchCheckRuns("owner/repo", HashMap.empty()));
    }

    /**
     * The repository {@code owner/repo} without checks that ran.
     */
    @Override
    default Either<GhError, RecentChecks> recentChecks() {
        return Either.right(new RecentChecks("owner/repo", List.empty()));
    }

    /**
     * No description, comments or reviews.
     */
    @Override
    default Either<GhError, List<PullRequestComment>> pullRequestComments(int number) {
        return Either.right(List.empty());
    }

    /**
     * No review threads, at commits that are not known.
     */
    @Override
    default Either<GhError, ReviewRemarks> reviewRemarks(int number) {
        return Either.right(ReviewRemarks.none());
    }

    /**
     * An open pull request {@code number} from branch {@code feature} into {@code main}, with nothing else to it.
     */
    @Override
    default Either<GhError, PullRequestDetails> pullRequestDetails(int number) {
        return Either.right(new PullRequestDetails(
                number,
                "A pull request",
                "someone",
                new Status(PullRequestDetails.State.OPEN, false, PullRequestDetails.Mergeable.UNKNOWN),
                new Branches("feature", "main"),
                new Activity(Instant.EPOCH, Instant.EPOCH),
                new Size(0, 0, 0, 1),
                new Conversation(
                        PullRequest.Review.NONE, List.empty(), 0, PullRequestDetails.ReviewThreads.NONE, List.empty()),
                List.empty()));
    }

    @Override
    default Either<GhFailure, Created> createPullRequest(GhBranch head, GhBranch base, NewPullRequest pullRequest) {
        return Either.right(new Created(
                Option.none(),
                List.of(CliGitHub.createCommand(head, base, pullRequest).executed())));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "view", String.valueOf(number), "--web")));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "view", url, "--web")));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> browse(GhBranch branch) {
        return Either.right(List.of(ExecutedCommand.of("gh", "browse", "--branch", branch.name())));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "checkout", String.valueOf(number))));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number, Path in) {
        return checkoutPullRequest(number);
    }

    @Override
    default Either<GhFailure, Merging> mergePullRequest(int number, Option<GhCommit> head, MergeMethod method) {
        return Either.right(new Merging(
                new MergeStatus.Pending("merge-" + number),
                List.of(MergeQuery.mergeCommand(number, head, method).executed())));
    }

    /**
     * Still pending.
     */
    @Override
    default Either<GhError, MergeStatus> mergeStatus(int number, String id) {
        return Either.right(new MergeStatus.Pending(id));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> closePullRequest(int number) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "close", String.valueOf(number))));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> setDraft(int number, boolean draft) {
        return Either.right(List.of(CliGitHub.draftCommand(number, draft).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> editPullRequest(int number, PullRequestEdit edit) {
        return Either.right(List.of(CliGitHub.editCommand(number, edit).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> commentOnPullRequest(int number, String body) {
        return Either.right(List.of(CliGitHub.commentCommand(number, body).executed()));
    }
}
