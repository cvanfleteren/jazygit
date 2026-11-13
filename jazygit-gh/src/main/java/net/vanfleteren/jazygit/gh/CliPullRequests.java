package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link PullRequests} through gh, of the repository gh finds from the git remotes of its working tree (ADR 114).
 */
final class CliPullRequests implements PullRequests {

    private static final Pattern CREATED_URL = Pattern.compile("/pull/(\\d{1,9})$");

    private final GhProcess process;

    CliPullRequests(GhProcess process) {
        this.process = process;
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

    static GhCommand draftCommand(int number, boolean draft) {
        return draft ? gh("pr", "ready", String.valueOf(number), "--undo") : gh("pr", "ready", String.valueOf(number));
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
    public Either<GhError, RepositoryPullRequests> ofRepository() {
        return process.readJson(RepositoryPullRequestsQuery.COMMAND, RepositoryPullRequestsQuery.DECODER);
    }

    @Override
    public Either<GhError, MyPullRequests> mine() {
        return process.readJson(MyPullRequestsQuery.COMMAND, MyPullRequestsQuery.DECODER);
    }

    @Override
    public Either<GhError, PullRequestPreview> preview(String url) {
        return process.readJson(PreviewQuery.command(url), PreviewQuery.DECODER)
                .flatMap(described -> process.read(PreviewQuery.diffCommand(url), PreviewQuery.DIFF_LIMIT)
                        .map(diff -> PreviewQuery.preview(described._1(), described._2(), diff)));
    }

    @Override
    public Either<GhError, PullRequestDetails> details(int number) {
        // gh fails the whole query when GitHub cannot compare the base with the head: then the rest is read on its own.
        return process.readJson(DetailsQuery.command(number), DetailsQuery.DECODER)
                .fold(
                        failed -> failed instanceof GhError.CommandFailed
                                ? process.readJson(DetailsQuery.withoutBehind(number), DetailsQuery.DECODER)
                                : Either.left(failed),
                        Either::right);
    }

    @Override
    public Either<GhError, byte[]> image(String url) {
        return process.readBytes(gh("api", url), MAX_IMAGE_BYTES);
    }

    @Override
    public Either<GhFailure, Created> create(GhBranch head, GhBranch base, NewPullRequest pullRequest) {
        return process.run(createCommand(head, base, pullRequest))
                .map(output -> new Created(createdNumber(output.stdout()), output.log()));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> checkout(int number) {
        return process.log(gh("pr", "checkout", String.valueOf(number)));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> checkout(int number, Path in) {
        return process.in(in).log(gh("pr", "checkout", String.valueOf(number)));
    }

    @Override
    public Either<GhFailure, Merging> merge(int number, Option<GhCommit> head, MergeMethod method) {
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
    public Either<GhFailure, List<ExecutedCommand>> close(int number) {
        return process.log(gh("pr", "close", String.valueOf(number)));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> setDraft(int number, boolean draft) {
        return process.log(draftCommand(number, draft));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> edit(int number, PullRequestEdit edit) {
        return process.log(editCommand(number, edit));
    }
}
