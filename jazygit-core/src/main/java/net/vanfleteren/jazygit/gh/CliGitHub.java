package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.gh.GhError.UnexpectedOutput;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Mergeable;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewThreads;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link GitHub} through the {@code gh} command line tool, for the repository in a working tree. gh finds the
 * GitHub repository from the git remotes itself.
 */
public final class CliGitHub implements GitHub {

    /*
     * Everything about one pull request the dialog shows, its latest commit for its checks. gh fills in the
     * repository of the working tree for {owner} and {repo}. GitHub no longer says when a commit was pushed, so the
     * latest of when it was opened, its latest commit and its latest force push tells when it was last pushed to; as
     * GitHub gives all three as UTC timestamps ending in Z, the latest of them sorts last as text.
     */
    static final String DETAILS_QUERY = """
            query($owner: String!, $name: String!, $number: Int!) { repository(owner: $owner, name: $name) { \
            nameWithOwner pullRequest(number: $number) { number title author { login } state isDraft headRefName \
            baseRefName headRefOid %s createdAt additions deletions changedFiles mergeable reviewDecision comments { totalCount } \
            labels(first: 100) { nodes { name } } latestReviews(first: 100) { nodes { author { login } state } } \
            reviewRequests(first: 100) { nodes { requestedReviewer { ... on User { login } ... on Bot { login } \
            ... on Mannequin { login } ... on Team { name } } } } \
            timelineItems(last: 1, itemTypes: [HEAD_REF_FORCE_PUSHED_EVENT]) { nodes { \
            ... on HeadRefForcePushedEvent { createdAt } } } \
            commits(last: 1) { totalCount nodes { commit { committedDate statusCheckRollup { contexts(first: 100) { \
            nodes { %s } } } %s } } } \
            reviewThreads(last: 100) { totalCount nodes { isResolved comments { totalCount } } } } } }""".formatted(StackFragment.FIELDS, CheckFragment.FIELDS, SuiteFragment.FIELDS);

    // The state of a check, in jq: a check run that is not completed yet is PENDING; a commit status gives its state.
    private static final String CHECK_STATE = """
            (if .__typename == "CheckRun" \
            then (if .status == "COMPLETED" then (.conclusion // "") else "PENDING" end) \
            else (.state // "") end)""";
    /*
     * A check as the fields of a line, in jq: its name, its state as in CHECK_STATE, and for a check run of a
     * workflow the fields of CheckFragment.CHECK_RUN_FIELDS, empty for the rest: its id, its workflow, its conclusion, when it
     * started and was done, and the id, creation, attempt and event of its workflow run.
     */
    private static final String CHECK_JQ = """
            def check: [((if .__typename == "CheckRun" then .name else .context end) // ""), %s] + \
            (if .__typename == "CheckRun" and .checkSuite.workflowRun != null \
            then (.checkSuite.workflowRun as $run | [(.databaseId | tostring), ($run.workflow.name // ""), \
            (.conclusion // ""), (.startedAt // ""), (.completedAt // ""), ($run.databaseId | tostring), \
            $run.createdAt, ($run.runAttempt | tostring), ($run.event // "")]) \
            else ["", "", "", "", "", "", "", "", ""] end); """.formatted(CHECK_STATE);
    private static final int CHECK_FIELD_COUNT = 1 + 11;
    /*
     * The workflow runs of a commit that wait to start, in jq: its suites of SuiteFragment.FIELDS of a workflow, not
     * completed, without a check run; and such a suite as the fields of a check of CHECK_JQ, queued, named after its
     * workflow and without an id of its own. A suite of another app has no workflow run: one that never adds a check
     * run stays queued for good.
     */
    private static final String WAITING_JQ = """
            def waiting: .checkSuites.nodes[]? \
            | select(.workflowRun != null and .status != "COMPLETED" and .checkRuns.totalCount == 0); \
            def waitingCheck: .workflowRun as $run | [($run.workflow.name // ""), "PENDING", "0", \
            ($run.workflow.name // ""), "", "", "", ($run.databaseId | tostring), $run.createdAt, \
            ($run.runAttempt | tostring), ($run.event // "")]; """;
    // StackFragment.FIELDS as the fields of a line, in jq: the number of the stack, the position, the size and its pull
    // requests as position:number:state, separated by commas, all empty when it is not stacked. An entry whose pull
    // request cannot be read is left out.
    private static final String STACK_JQ = """
            def stack: [(.stack.number // "" | tostring), (.stackEntry.position // "" | tostring), \
            (.stack.size // "" | tostring), \
            ([.stack.entries.nodes[]? | select(.pullRequest != null) \
            | "\\(.position):\\(.pullRequest.number):\\(.pullRequest.state)"] | join(","))]; """;
    private static final int STACK_FIELD_COUNT = 4;

    /*
     * Tab separated lines, each starting with what it is: a "repo" line with the repository as owner/name, one "pr"
     * line with the fields of the pull request, one "threads" line with how many review threads it has, how many of
     * them were read, and how many of those are unresolved and how many comments they hold, a "stack" line with the fields of STACK_JQ when it is stacked,
     * then a "label" line per label, a "review" line per latest review, a "requested" line per requested reviewer
     * and a "check" line per check, with the fields of CHECK_JQ, and per workflow run that waits to start.
     */
    static final String DETAILS_JQ = STACK_JQ + CHECK_JQ + WAITING_JQ + """
            .data.repository | (["repo", .nameWithOwner] | @tsv), (.pullRequest | \
            ((["pr", (.number | tostring), .title, (.author.login // ""), .state, \
            (.isDraft | tostring), .headRefName, .baseRefName, .createdAt, \
            ([.createdAt, .commits.nodes[0].commit.committedDate, .timelineItems.nodes[0].createdAt] \
            | map(select(. != null)) | max), (.additions | tostring), \
            (.deletions | tostring), (.changedFiles | tostring), (.mergeable // ""), (.reviewDecision // ""), \
            (.comments.totalCount | tostring), (.headRefOid // ""), (.commits.totalCount | tostring)] | @tsv), \
            (select(.stack != null) | ["stack"] + stack | @tsv), \
            (.reviewThreads | ["threads", (.totalCount | tostring), (.nodes | length | tostring), \
            ([.nodes[] | select(.isResolved | not)] | length | tostring), \
            ([.nodes[].comments.totalCount] | add // 0 | tostring)] | @tsv), \
            (.labels.nodes[]? | ["label", .name] | @tsv), \
            (.latestReviews.nodes[]? | ["review", (.author.login // ""), .state] | @tsv), \
            (.reviewRequests.nodes[]? | ["requested", ((.requestedReviewer.login // .requestedReviewer.name) // "")] \
            | @tsv), \
            (.commits.nodes[0].commit.statusCheckRollup.contexts.nodes[]? | ["check"] + check | @tsv), \
            (.commits.nodes[0].commit | waiting | ["check"] + waitingCheck | @tsv)))""";
    private static final int DETAIL_FIELD_COUNT = 18;
    /*
     * Tab separated lines, one per part of the conversation: what it is ("description", "comment", or the state of
     * a review), its id, its author, when it was written, whether it was edited, whether it is hidden and why, and
     * its text. The description first, then the rest oldest first. A review that only commented and has no text is
     * what inline comments hang from; a pending one is not submitted yet.
     */
    private static final String COMMENTS_JQ = """
            (select(.body != "") | ["description", "description", (.author.login // ""), .createdAt, "false", \
            "false", "", .body] | @tsv), \
            ([(.comments[]? | ["comment", .id, (.author.login // ""), .createdAt, (.includesCreatedEdit | tostring), \
            (.isMinimized | tostring), (.minimizedReason // ""), .body]), \
            (.reviews[]? | select(.state != "PENDING") | select(.body != "" or .state != "COMMENTED") \
            | [.state, .id, (.author.login // ""), .submittedAt, (.includesCreatedEdit | tostring), "false", "", \
            .body])] | sort_by(.[3]) | .[] | @tsv)""";
    private static final int COMMENT_FIELD_COUNT = 8;

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

    static GhCommand browseCommand(GhBranch branch) {
        return gh("browse", "--branch", branch.name());
    }

    /**
     * Where a pull request sits in its stack, from the fields of {@link #STACK_JQ}: none when they are all empty,
     * as it is not stacked, and a failure when they are not what they should be.
     */
    private static Try<Option<PullRequestStack>> stack(List<String> fields) {
        return fields.forAll(String::isEmpty)
                ? Try.success(Option.none())
                : Try.of(() -> Option.of(new PullRequestStack(
                        Integer.parseInt(fields.get(0)),
                        Integer.parseInt(fields.get(1)),
                        Integer.parseInt(fields.get(2)),
                        List.of(fields.get(3).split(","))
                                .filter(entry -> !entry.isEmpty())
                                .map(CliGitHub::stackEntry))));
    }

    /**
     * A pull request of a stack, from position:number:state; throws when it is not that.
     */
    private static PullRequestStack.Entry stackEntry(String entry) {
        String[] parts = entry.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException(entry);
        }
        return new PullRequestStack.Entry(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), PullRequestDetails.State.valueOf(parts[2]));
    }

    /**
     * Parses the lines the jq filter of {@link #pullRequestDetails(int)} prints.
     */
    static Either<UnexpectedOutput, PullRequestDetails> parseDetails(String raw) {
        List<List<String>> lines = List.of(raw.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> List.of(line.split("\t", -1)).map(CliGitHub::unescape));
        Option<List<String>> unknown = lines.find(fields -> !known(fields));
        if (unknown.isDefined()) {
            return Either.left(new UnexpectedOutput(unknown.get().mkString("\t")));
        }
        List<String> requested = lines.filter(f -> f.head().equals("requested")).map(f -> f.get(1));
        List<Reviewer> reviewers = lines.filter(f -> f.head().equals("review"))
                .flatMap(f -> reviewerState(f.get(2)).map(state -> new Reviewer(f.get(1), state)))
                // Asked to review again, after an earlier review.
                .filter(reviewer -> !requested.contains(reviewer.login()))
                .appendAll(requested.map(login -> new Reviewer(login, ReviewerState.REQUESTED)));
        List<String> labels = lines.filter(f -> f.head().equals("label")).map(f -> f.get(1));
        Option<ReviewThreads> threads = lines.find(f -> f.head().equals("threads"))
                .flatMap(f -> Try.of(() -> new ReviewThreads(
                                Integer.parseInt(f.get(1)),
                                Integer.parseInt(f.get(2)),
                                Integer.parseInt(f.get(3)),
                                Integer.parseInt(f.get(4))))
                        .toOption());
        List<Check> checks = lines.filter(f -> f.head().equals("check")).map(f -> check(f.tail()));
        String repository =
                lines.find(f -> f.head().equals("repo")).map(f -> f.get(1)).getOrElse("");
        Try<Option<PullRequestStack>> stack = lines.find(f -> f.head().equals("stack"))
                .map(f -> stack(f.tail()))
                .getOrElse(Try.success(Option.none()));
        return lines.find(f -> f.head().equals("pr"))
                .flatMap(f -> threads.flatMap(reviewThreads -> stack.mapTry(pullRequestStack -> new PullRequestDetails(
                                Integer.parseInt(f.get(1)),
                                f.get(2),
                                f.get(3),
                                new PullRequestDetails.Status(
                                        PullRequestDetails.State.valueOf(f.get(4)),
                                        f.get(5).equals("true"),
                                        mergeable(f.get(13))),
                                new PullRequestDetails.Branches(
                                        f.get(6), f.get(7), GhCommit.parse(f.get(16)), Option.none()),
                                new PullRequestDetails.Activity(Instant.parse(f.get(8)), Instant.parse(f.get(9))),
                                new PullRequestDetails.Size(
                                        Integer.parseInt(f.get(10)),
                                        Integer.parseInt(f.get(11)),
                                        Integer.parseInt(f.get(12)),
                                        Integer.parseInt(f.get(17))),
                                new PullRequestDetails.Conversation(
                                        review(f.get(14)),
                                        reviewers,
                                        Integer.parseInt(f.get(15)),
                                        reviewThreads,
                                        labels),
                                checks,
                                pullRequestStack,
                                repository))
                        .toOption()))
                .toEither(() -> new UnexpectedOutput(raw));
    }

    /**
     * The check that {@code fields} of a line of {@link #CHECK_JQ} tell. A check without a state yet, like a status
     * nobody set, is neither passing nor failing. A check run whose timing cannot be read is not timed.
     */
    static Check check(List<String> fields) {
        Checks state = CheckFragment.checks(List.of(fields.get(1)).filter(value -> !value.isEmpty()));
        Option<CheckRun> run =
                fields.size() < CHECK_FIELD_COUNT - 1 || fields.get(2).isEmpty()
                        ? Option.none()
                        : Try.of(() -> new CheckRun(
                                        Long.parseLong(fields.get(2)),
                                        fields.get(3),
                                        fields.get(4),
                                        instant(fields.get(5)),
                                        instant(fields.get(6)),
                                        Long.parseLong(fields.get(7)),
                                        Instant.parse(fields.get(8)),
                                        Integer.parseInt(fields.get(9)),
                                        fields.get(10)))
                                .toOption();
        return new Check(fields.get(0), state, run);
    }

    private static Option<Instant> instant(String value) {
        return value.isEmpty() ? Option.none() : Option.of(Instant.parse(value));
    }

    private static boolean known(List<String> fields) {
        return switch (fields.head()) {
            case "pr" -> fields.size() == DETAIL_FIELD_COUNT;
            case "label", "requested" -> fields.size() == 2;
            case "repo" -> fields.size() == 2;
            case "review" -> fields.size() == 3;
            // Without the timing of CHECK_JQ too, as it was read before.
            case "check" -> fields.size() == 3 || fields.size() == CHECK_FIELD_COUNT;
            case "stack" -> fields.size() == 1 + STACK_FIELD_COUNT;
            case "threads" -> fields.size() == 5;
            default -> false;
        };
    }

    /**
     * Parses the lines the jq filter of {@link #pullRequestComments(int)} prints.
     */
    static Either<UnexpectedOutput, List<PullRequestComment>> parseComments(String raw) {
        return Either.sequenceRight(List.of(raw.split("\n"))
                        .filter(line -> !line.isBlank())
                        .map(line -> comment(List.of(line.split("\t", -1)).map(CliGitHub::unescape))
                                .toEither(() -> new UnexpectedOutput(line))))
                .map(List::ofAll);
    }

    private static Option<PullRequestComment> comment(List<String> f) {
        return Option.when(f.size() == COMMENT_FIELD_COUNT, f)
                .flatMap(fields -> commentKind(fields.get(0)))
                .flatMap(kind -> Try.of(() -> new PullRequestComment(
                                new PullRequestComment.Id(f.get(1)),
                                kind,
                                f.get(2),
                                Instant.parse(f.get(3)),
                                f.get(7),
                                f.get(4).equals("true"),
                                Option.when(f.get(5).equals("true"), f.get(6))))
                        .toOption());
    }

    private static Option<PullRequestComment.Kind> commentKind(String kind) {
        return switch (kind) {
            case "description" -> Option.of(PullRequestComment.Kind.DESCRIPTION);
            case "comment" -> Option.of(PullRequestComment.Kind.COMMENT);
            case "APPROVED" -> Option.of(PullRequestComment.Kind.APPROVED);
            case "CHANGES_REQUESTED" -> Option.of(PullRequestComment.Kind.CHANGES_REQUESTED);
            case "COMMENTED" -> Option.of(PullRequestComment.Kind.COMMENTED);
            case "DISMISSED" -> Option.of(PullRequestComment.Kind.DISMISSED);
            default -> Option.none();
        };
    }

    /**
     * {@code field} as it was before jq's {@code @tsv} escaped it.
     */
    static String unescape(String field) {
        StringBuilder out = new StringBuilder(field.length());
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == '\\' && i + 1 < field.length()) {
                char next = field.charAt(++i);
                out.append(
                        switch (next) {
                            case 't' -> '\t';
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            default -> next;
                        });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static Option<ReviewerState> reviewerState(String state) {
        return switch (state) {
            case "APPROVED" -> Option.of(ReviewerState.APPROVED);
            case "CHANGES_REQUESTED" -> Option.of(ReviewerState.CHANGES_REQUESTED);
            case "COMMENTED" -> Option.of(ReviewerState.COMMENTED);
            // Dismissed, or not submitted yet.
            default -> Option.none();
        };
    }

    private static Mergeable mergeable(String state) {
        return switch (state) {
            case "MERGEABLE" -> Mergeable.MERGEABLE;
            case "CONFLICTING" -> Mergeable.CONFLICTING;
            default -> Mergeable.UNKNOWN;
        };
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
    public Either<GhError, List<PullRequest>> pullRequests() {
        return process.readJson(PullRequestsQuery.COMMAND, PullRequestsQuery.DECODER);
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
        return process.read(
                        gh(
                                "api",
                                "graphql",
                                "-F",
                                "owner={owner}",
                                "-F",
                                "name={repo}",
                                "-F",
                                "number=" + number,
                                "-f",
                                "query=" + DETAILS_QUERY,
                                "--jq",
                                DETAILS_JQ),
                        CliGitHub::parseDetails)
                .map(details -> details.branches()
                        .headCommit()
                        .filter(head -> details.status().state() == PullRequestDetails.State.OPEN)
                        .flatMap(head -> behindBase(details.branches().base(), head))
                        .map(details::withBehindBase)
                        .getOrElse(details));
    }

    /**
     * How many commits base branch {@code base} has that commit {@code head} does not; none when that cannot be read,
     * also when the read fails, as the rest of the details read fine and come back without it.
     */
    private Option<Integer> behindBase(String base, GhCommit head) {
        return process.readJson(BehindQuery.command(base, head), BehindQuery.DECODER)
                .toOption()
                .flatMap(behind -> behind);
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
        return process.read(
                gh(
                        "pr",
                        "view",
                        String.valueOf(number),
                        "--json",
                        "body,author,createdAt,comments,reviews",
                        "--jq",
                        COMMENTS_JQ),
                CliGitHub::parseComments);
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
    public Either<GhFailure, List<ExecutedCommand>> commentOnPullRequest(int number, String body) {
        return process.log(commentCommand(number, body));
    }

    static GhCommand commentCommand(int number, String body) {
        return gh("pr", "comment", String.valueOf(number), "--body", body);
    }
}
