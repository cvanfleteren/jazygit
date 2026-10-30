package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.gh.GhError.UnexpectedOutput;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link GitHub} through the {@code gh} command line tool, for the repository in a working tree. gh finds the
 * GitHub repository from the git remotes itself.
 */
public final class CliGitHub implements GitHub {

    private static final Pattern CREATED_URL = Pattern.compile("/pull/(\\d{1,9})$");

    private final Path worktree;
    private final GhProcess process;
    // As gh's own active accounts: with a token in the environment, gh only knows of that one.
    private final GhProcess unconfigured;

    /**
     * GitHub for the repository in {@code worktree}, through the gh at {@code executable}, as the account of
     * {@code token}.
     */
    public CliGitHub(Path worktree, String executable, GhToken token) {
        this.worktree = worktree;
        this.process = new GhProcess(worktree, executable, token);
        this.unconfigured = new GhProcess(worktree, executable, GhToken.NONE);
    }

    /**
     * The version in the first line {@code gh --version} prints, e.g. {@code gh version 2.102.0 (2026-09-30)}.
     */
    static Either<UnexpectedOutput, String> parseVersion(String raw) {
        return List.of(raw.split("\\R"))
                .headOption()
                .map(String::strip)
                .filter(line -> line.startsWith("gh version "))
                .map(line -> line.substring("gh version ".length()).split(" ")[0])
                .toEither(() -> new UnexpectedOutput(raw));
    }

    /**
     * The number in the URL of the new pull request, which is the last thing {@code gh pr create} prints.
     */
    static Option<Integer> createdNumber(String stdout) {
        Matcher url = CREATED_URL.matcher(stdout.strip());
        return Option.when(url.find(), () -> Integer.parseInt(url.group(1)));
    }

    /**
     * The command that creates a pull request from {@code head} into {@code base}: with the title and the body
     * given, so gh does not ask for them, and as a draft if asked.
     */
    static GhCommand createCommand(GhBranch head, GhBranch base, NewPullRequest pullRequest) {
        return gh(List.of(
                        "pr",
                        "create",
                        "--head",
                        head.name(),
                        "--base",
                        base.name(),
                        "--title",
                        pullRequest.title(),
                        "--body",
                        pullRequest.body())
                .appendAll(Option.when(pullRequest.draft(), "--draft"))
                .toJavaArray(String[]::new));
    }

    static GhCommand editCommand(int number, PullRequestEdit edit) {
        return gh(List.of("pr", "edit", String.valueOf(number))
                .appendAll(edit.title().toList().flatMap(title -> List.of("--title", title)))
                .appendAll(edit.body().toList().flatMap(body -> List.of("--body", body)))
                .appendAll(edit.base().toList().flatMap(base -> List.of("--base", base.name())))
                .toJavaArray(String[]::new));
    }

    static GhCommand browseCommand(GhBranch branch) {
        return gh("browse", "--branch", branch.name());
    }

    static Review review(String decision) {
        return switch (decision) {
            case "APPROVED" -> Review.APPROVED;
            case "CHANGES_REQUESTED" -> Review.CHANGES_REQUESTED;
            case "REVIEW_REQUIRED" -> Review.REQUIRED;
            default -> Review.NONE;
        };
    }

    @Override
    public Either<GhError, String> version(String executable) {
        return new GhProcess(worktree, executable, GhToken.NONE).read(gh("--version"), CliGitHub::parseVersion);
    }

    @Override
    public Either<GhError, List<GhLogin>> logins() {
        // Exits with 0 also when an account's token does not work anymore, which the state says.
        return unconfigured.readJson(LoginsQuery.COMMAND, LoginsQuery.DECODER);
    }

    @Override
    public Either<GhError, RepositoryPullRequests> repositoryPullRequests() {
        return process.readJson(RepositoryPullRequestsQuery.COMMAND, RepositoryPullRequestsQuery.DECODER);
    }

    @Override
    public Either<GhError, MyPullRequests> myPullRequests() {
        return process.readJson(MyPullRequestsQuery.COMMAND, MyPullRequestsQuery.DECODER);
    }

    @Override
    public Either<GhError, PullRequestPreview> pullRequestPreview(String url) {
        return process.readJson(PreviewQuery.command(url), PreviewQuery.DECODER)
                .flatMap(described -> process.read(PreviewQuery.diffCommand(url), Either::right)
                        .map(diff -> PreviewQuery.preview(described._1(), described._2(), diff)));
    }

    @Override
    public Either<GhError, BranchChecks> branchChecks() {
        return process.readJson(BranchesQuery.COMMAND, BranchesQuery.DECODER);
    }

    @Override
    public Either<GhError, PullRequestDetails> pullRequestDetails(int number) {
        // gh fails the whole query when GitHub cannot compare the base with the head: then the rest is read on its own.
        return process.readJson(DetailsQuery.command(number), DetailsQuery.DECODER)
                .fold(
                        failed -> failed instanceof GhError.CommandFailed
                                ? process.readJson(DetailsQuery.withoutBehind(number), DetailsQuery.DECODER)
                                : Either.left(failed),
                        Either::right);
    }

    @Override
    public Either<GhError, BranchCheckRuns> branchCheckRuns(List<String> branches) {
        return process.readJson(BranchCheckRunsQuery.command(branches), BranchCheckRunsQuery.decoder(branches.size()));
    }

    @Override
    public Either<GhError, RecentChecks> recentChecks() {
        return process.readJson(RecentChecksQuery.COMMAND, RecentChecksQuery.DECODER);
    }

    @Override
    public Either<GhError, List<PullRequestComment>> pullRequestComments(int number) {
        return process.readJson(CommentsQuery.command(number), CommentsQuery.DECODER);
    }

    @Override
    public Either<GhError, ReviewRemarks> reviewRemarks(int number) {
        return process.readJson(ReviewThreadsQuery.command(number), ReviewThreadsQuery.DECODER);
    }

    @Override
    public Either<GhFailure, Created> createPullRequest(GhBranch head, GhBranch base, NewPullRequest pullRequest) {
        return process.run(createCommand(head, base, pullRequest))
                .map(output -> new Created(createdNumber(output.stdout()), output.log()));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number) {
        return process.log(gh("pr", "view", String.valueOf(number), "--web"));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url) {
        return process.log(gh("pr", "view", url, "--web"));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> browse(GhBranch branch) {
        return process.log(browseCommand(branch));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number) {
        return process.log(gh("pr", "checkout", String.valueOf(number)));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number, Path in) {
        return process.in(in).log(gh("pr", "checkout", String.valueOf(number)));
    }

    @Override
    public Either<GhFailure, Merging> mergePullRequest(int number, Option<GhCommit> head, MergeMethod method) {
        return process.run(MergeQuery.mergeCommand(number, head, method))
                .flatMap(output -> GhProcess.decoded(output.stdout(), MergeQuery.DECODER)
                        .map(status -> new Merging(status, output.log()))
                        .mapLeft(unexpected -> new GhFailure(unexpected, output.log())));
    }

    @Override
    public Either<GhError, MergeStatus> mergeStatus(int number, String id) {
        return process.readJson(MergeQuery.statusCommand(number, id), MergeQuery.DECODER);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> closePullRequest(int number) {
        return process.log(gh("pr", "close", String.valueOf(number)));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> setDraft(int number, boolean draft) {
        return process.log(draftCommand(number, draft));
    }

    static GhCommand draftCommand(int number, boolean draft) {
        return draft ? gh("pr", "ready", String.valueOf(number), "--undo") : gh("pr", "ready", String.valueOf(number));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> editPullRequest(int number, PullRequestEdit edit) {
        return process.log(editCommand(number, edit));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> commentOnPullRequest(int number, String body) {
        return process.log(commentCommand(number, body));
    }

    @Override
    public Either<GhFailure, Started> startReviewThread(String pullRequest, NewReviewThread thread) {
        return process.run(ReviewThreadMutations.start(pullRequest, thread))
                .flatMap(output -> GhProcess.decoded(output.stdout(), ReviewThreadMutations.STARTED)
                        .map(id -> new Started(id, output.log()))
                        .mapLeft(unexpected -> new GhFailure(unexpected, output.log())));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> replyToReviewThread(String thread, String body) {
        return process.log(ReviewThreadMutations.reply(thread, body));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> resolveReviewThread(String thread, boolean resolved) {
        return process.log(ReviewThreadMutations.resolve(thread, resolved));
    }

    static GhCommand commentCommand(int number, String body) {
        return gh("pr", "comment", String.valueOf(number), "--body", body);
    }
}
