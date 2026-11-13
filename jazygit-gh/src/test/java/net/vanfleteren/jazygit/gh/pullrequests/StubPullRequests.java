package net.vanfleteren.jazygit.gh.pullrequests;

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
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link PullRequests} for tests: a repository without pull requests, whose operations all succeed without running
 * anything. Fakes override what they need.
 */
public interface StubPullRequests extends PullRequests {

    /**
     * The repository {@code owner/repo} without pull requests.
     */
    @Override
    default Either<GhError, RepositoryPullRequests> ofRepository() {
        return Either.right(new RepositoryPullRequests("owner/repo", PullRequestList.EMPTY));
    }

    @Override
    default Either<GhError, MyPullRequests> mine() {
        return Either.right(new MyPullRequests(PullRequestList.EMPTY, PullRequestList.EMPTY));
    }

    /**
     * A pull request into {@code main} without a description or changes.
     */
    @Override
    default Either<GhError, PullRequestPreview> preview(String url) {
        return Either.right(new PullRequestPreview(new GhBranch("main"), "", "", false));
    }

    /**
     * An open pull request {@code number} from branch {@code feature} into {@code main}, with nothing else to it.
     */
    @Override
    default Either<GhError, PullRequestDetails> details(int number) {
        return Either.right(new PullRequestDetails(
                number,
                "A pull request",
                "someone",
                new Status(PullRequestDetails.State.OPEN, false, PullRequestDetails.Mergeable.UNKNOWN),
                new Branches(new GhBranch("feature"), new GhBranch("main")),
                new Activity(Instant.EPOCH, Instant.EPOCH),
                new Size(0, 0, 0, 1),
                new Conversation(
                        PullRequest.Review.NONE, List.empty(), 0, PullRequestDetails.ReviewThreads.NONE, List.empty()),
                List.empty()));
    }

    /**
     * No bytes, which is no image.
     */
    @Override
    default Either<GhError, byte[]> image(String url) {
        return Either.right(new byte[0]);
    }

    @Override
    default Either<GhFailure, Created> create(GhBranch head, GhBranch base, NewPullRequest pullRequest) {
        return Either.right(new Created(
                Option.none(),
                List.of(CliPullRequests.createCommand(head, base, pullRequest).executed())));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> checkout(int number) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "checkout", String.valueOf(number))));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> checkout(int number, Path in) {
        return checkout(number);
    }

    @Override
    default Either<GhFailure, Merging> merge(int number, Option<GhCommit> head, MergeMethod method) {
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
    default Either<GhFailure, List<ExecutedCommand>> close(int number) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "close", String.valueOf(number))));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> setDraft(int number, boolean draft) {
        return Either.right(List.of(CliPullRequests.draftCommand(number, draft).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> edit(int number, PullRequestEdit edit) {
        return Either.right(List.of(CliPullRequests.editCommand(number, edit).executed()));
    }
}
