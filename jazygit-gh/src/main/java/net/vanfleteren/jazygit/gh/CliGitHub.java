package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.gh.GhError.UnexpectedOutput;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.json.Decoder;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link GitHub} through the {@code gh} command line tool, for the repository in a working tree. gh finds the
 * GitHub repository from the git remotes itself.
 */
public final class CliGitHub implements GitHub {

    // Deletes a comment of the conversation by the node id it is read with (ADR 64).
    static final String DELETE_COMMENT = """
            mutation($comment: ID!) { deleteIssueComment(input: { id: $comment }) { clientMutationId } }""";

    // Edits a comment of the conversation by the node id it is read with (ADR 65).
    static final String EDIT_COMMENT = """
            mutation($comment: ID!, $body: String!) {
              updateIssueComment(input: { id: $comment, body: $body }) { issueComment { body } }
            }""";

    // The body of the comment of the conversation, as GitHub holds it once it is edited.
    static final Decoder<String> EDITED_COMMENT =
            Decoder.at("data.updateIssueComment.issueComment.body", Decoder.string());

    private static final Pattern CREATED_URL = Pattern.compile("/pull/(\\d{1,9})$");

    private final Path worktree;
    private final GhProcess process;
    // As gh's own active accounts: with a token in the environment, gh only knows of that one.
    private final GhProcess unconfigured;
    private final GhToken token;
    // The GitHub host of the remotes, which can be an ssh alias.
    private final Supplier<String> host;
    // The browser set for an account, if any (ADR 88).
    private final Function<GhAccount, Option<String>> browsers;
    // The environment and the system the browser is chosen by, as gh chooses it (ADR 85).
    private final Function<String, Option<String>> environment;
    private final String os;

    /**
     * GitHub for the repository in {@code worktree}, through the gh at {@code executable}, as the account of
     * {@code token}.
     */
    public CliGitHub(Path worktree, String executable, GhToken token) {
        this(worktree, executable, token, () -> GhAccount.GITHUB, account -> Option.none());
    }

    /**
     * GitHub as {@link #CliGitHub(Path, String, GhToken)}, for a repository whose remotes are on {@code host}, opening
     * links in the browser {@code browsers} gives the account the repository uses, if any (ADR 88).
     */
    public CliGitHub(
            Path worktree,
            String executable,
            GhToken token,
            Supplier<String> host,
            Function<GhAccount, Option<String>> browsers) {
        this(
                worktree,
                executable,
                token,
                host,
                browsers,
                name -> Option.of(System.getenv(name)),
                System.getProperty("os.name", ""));
    }

    /**
     * GitHub as {@link #CliGitHub(Path, String, GhToken)}, choosing the browser by the variables {@code environment}
     * gives and the system {@code os}.
     */
    CliGitHub(
            Path worktree, String executable, GhToken token, Function<String, Option<String>> environment, String os) {
        this(worktree, executable, token, () -> GhAccount.GITHUB, account -> Option.none(), environment, os);
    }

    /**
     * GitHub as {@link #CliGitHub(Path, String, GhToken, Supplier, Function)}, choosing the browser by the variables
     * {@code environment} gives and the system {@code os}.
     */
    CliGitHub(
            Path worktree,
            String executable,
            GhToken token,
            Supplier<String> host,
            Function<GhAccount, Option<String>> browsers,
            Function<String, Option<String>> environment,
            String os) {
        this.worktree = worktree;
        this.process = new GhProcess(worktree, executable, token);
        this.unconfigured = new GhProcess(worktree, executable, GhToken.NONE);
        this.token = token;
        this.host = host;
        this.browsers = browsers;
        this.environment = environment;
        this.os = os;
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
                .flatMap(described -> process.read(PreviewQuery.diffCommand(url), PreviewQuery.DIFF_LIMIT)
                        .map(diff -> PreviewQuery.preview(described._1(), described._2(), diff)));
    }

    @Override
    public Either<GhError, BranchChecks> branchChecks() {
        return process.readJson(BranchesQuery.COMMAND, BranchesQuery.DECODER);
    }

    @Override
    public Either<GhError, List<String>> protectedBranches() {
        return process.readJson(ProtectedBranchesQuery.COMMAND, ProtectedBranchesQuery.DECODER);
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
    public Either<GhError, byte[]> image(String url) {
        return process.readBytes(GhCommand.gh("api", url), MAX_IMAGE_BYTES);
    }

    @Override
    public Either<GhFailure, Created> createPullRequest(GhBranch head, GhBranch base, NewPullRequest pullRequest) {
        return process.run(createCommand(head, base, pullRequest))
                .map(output -> new Created(createdNumber(output.stdout()), output.log()));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number) {
        return web(gh("pr", "view", String.valueOf(number), "--web"), accountBrowser());
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url) {
        return web(gh("pr", "view", url, "--web"), accountBrowser());
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openInBrowser(PullRequestLink link) {
        Option<String> account = accountBrowser();
        if (link.host().isEmpty()) {
            return web(gh("pr", "view", String.valueOf(link.number()), "--web"), account);
        }
        return account.orElse(() -> ghBrowser(link.host()).map(ChosenBrowser::command))
                .map(browser -> launch(browser, link.url()))
                .getOrElse(() -> web(gh("pr", "view", link.pullRequest(), "--web"), account));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openLink(String url) {
        return accountBrowser()
                .orElse(() -> ghBrowser(SshHost.resolve(worktree, host.get())).map(ChosenBrowser::command))
                .map(browser -> launch(browser, url))
                .getOrElse(() -> Either.left(new GhFailure(new GhError.NoBrowser(), List.empty())));
    }

    @Override
    public Option<GhAccount> account() {
        return token.configured()
                .map(configured -> new GhAccount(SshHost.resolve(worktree, configured.host()), configured.user()))
                .orElse(() -> activeAccount(SshHost.resolve(worktree, host.get())));
    }

    @Override
    public Option<ChosenBrowser> ghBrowser(String host) {
        return Browser.chosen(environment, () -> configuredBrowser(host), os);
    }

    /**
     * gh's active account on {@code host}, as its own files name it; none when a token in the environment makes gh
     * run as the account of that token instead, which its files do not name, or when gh names none.
     */
    private Option<GhAccount> activeAccount(String host) {
        List<String> tokens = GhAccount.isGitHubCom(host)
                ? List.of("GH_TOKEN", "GITHUB_TOKEN")
                : List.of("GH_ENTERPRISE_TOKEN", "GITHUB_ENTERPRISE_TOKEN");
        if (tokens.exists(name -> environment.apply(name).exists(value -> !value.isBlank()))) {
            return Option.none();
        }
        return unconfigured
                .read(gh("config", "get", "user", "-h", host), raw -> Either.right(raw.strip()))
                .toOption()
                .filter(user -> !user.isEmpty())
                .map(user -> new GhAccount(host, user));
    }

    /**
     * The browser set for the account the repository uses now, if any (ADR 88).
     */
    private Option<String> accountBrowser() {
        return account().flatMap(browsers).map(String::strip).filter(browser -> !browser.isEmpty());
    }

    /**
     * Runs {@code command}, which opens something in the browser, in {@code browser} when one is set for the account.
     */
    private Either<GhFailure, List<ExecutedCommand>> web(GhCommand command, Option<String> browser) {
        return process.log(
                browser.map(set -> command.withEnvironment("GH_BROWSER", set)).getOrElse(command));
    }

    /**
     * gh's {@code browser} setting for {@code host}, or its own; none when it is not set, or cannot be read.
     */
    private Option<String> configuredBrowser(String host) {
        return process.read(gh("config", "get", "browser", "--host", host), Either::right)
                .toOption();
    }

    /**
     * Opens {@code url} in {@code browser}, as it is set, without waiting for it to close.
     */
    private Either<GhFailure, List<ExecutedCommand>> launch(String browser, String url) {
        List<String> commandLine = Browser.commandLine(browser, url);
        return Exec.launch(worktree, commandLine, Browser.SETTLE)
                .mapLeft(failure -> new GhFailure(
                        switch (failure) {
                            case ExecFailure.Interrupted() -> new GhError.Interrupted("browser");
                            case ExecFailure.NonZero(int exitCode, var stderr, var stdout) ->
                                new GhError.BrowserFailed(browser, Option.of(exitCode));
                            default -> new GhError.BrowserFailed(browser, Option.none());
                        },
                        List.of(new ExecutedCommand(commandLine))))
                .map(List::of);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> browse(GhBranch branch) {
        return web(browseCommand(branch), accountBrowser());
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
    public Either<GhFailure, List<ExecutedCommand>> createStack(List<Integer> pullRequests) {
        return process.log(StackMutations.create(pullRequests));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> addToStack(int stack, List<Integer> pullRequests) {
        return process.log(StackMutations.add(stack, pullRequests));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> unstack(int stack) {
        return process.log(StackMutations.unstack(stack));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> commentOnPullRequest(int number, String body) {
        return process.log(commentCommand(number, body));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> startReviewThread(
            int number, String commit, NewReviewThread thread) {
        return process.log(ReviewThreadMutations.start(number, commit, thread));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> replyToReviewThread(int number, String comment, String body) {
        return process.log(ReviewThreadMutations.reply(number, comment, body));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> resolveReviewThread(String thread, boolean resolved) {
        return process.log(ReviewThreadMutations.resolve(thread, resolved));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> deleteReviewComment(String comment) {
        return delete(ReviewThreadMutations.delete(comment));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> deletePullRequestComment(String comment) {
        return delete(deleteCommentCommand(comment));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> reactToReviewComment(
            String comment, Reaction reaction, boolean given) {
        return process.log(ReviewThreadMutations.react(comment, reaction, given))
                .mapLeft(failure -> GhProcess.gone(failure.error())
                        ? new GhFailure(new GhError.Gone(), failure.commands())
                        : failure);
    }

    @Override
    public Either<GhFailure, Edited> editReviewComment(String comment, String body) {
        return edit(ReviewThreadMutations.edit(comment, body), ReviewThreadMutations.EDITED);
    }

    @Override
    public Either<GhFailure, Edited> editPullRequestComment(String comment, String body) {
        return edit(editCommentCommand(comment, body), EDITED_COMMENT);
    }

    static GhCommand commentCommand(int number, String body) {
        return gh("pr", "comment", String.valueOf(number), "--body", body);
    }

    static GhCommand deleteCommentCommand(String comment) {
        return gh("api", "graphql", "-f", "query=" + DELETE_COMMENT, "-f", "comment=" + comment);
    }

    static GhCommand editCommentCommand(String comment, String body) {
        return gh("api", "graphql", "-f", "query=" + EDIT_COMMENT, "-f", "comment=" + comment, "-f", "body=" + body);
    }

    // Runs command, which edits a comment, and gives the body GitHub now holds, which decoder reads from what it says.
    // A comment GitHub no longer has is gone: unlike a delete, its edit is not done.
    private Either<GhFailure, Edited> edit(GhCommand command, Decoder<String> decoder) {
        return process.run(command)
                .mapLeft(failure -> GhProcess.gone(failure.error())
                        ? new GhFailure(new GhError.Gone(), failure.commands())
                        : failure)
                .flatMap(output -> GhProcess.decoded(output.stdout(), decoder)
                        .map(body -> new Edited(body, output.log()))
                        .mapLeft(unexpected -> new GhFailure(unexpected, output.log())));
    }

    // Runs command, which deletes a comment: one GitHub no longer has was deleted already, as by someone else.
    private Either<GhFailure, List<ExecutedCommand>> delete(GhCommand command) {
        return process.log(command)
                .fold(
                        failure -> GhProcess.gone(failure.error())
                                ? Either.right(failure.commands())
                                : Either.left(failure),
                        Either::right);
    }
}
