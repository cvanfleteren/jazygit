package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
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

    // The most lines of a diff of a pull request that are shown, as for the diffs of git.
    static final int MAX_DIFF_LINES = 5000;

    // The state of a check, in jq: a check run that is not completed yet is PENDING; a commit status gives its state.
    private static final String CHECK_STATE = """
            (if .__typename == "CheckRun" \
            then (if .status == "COMPLETED" then (.conclusion // "") else "PENDING" end) \
            else (.state // "") end)""";
    // Where a pull request sits in its stack, and the pull requests of it, in GraphQL: both null when it is not
    // stacked.
    private static final String STACK_FIELDS = """
            stackEntry { position } stack { number size \
            entries(first: 100) { nodes { position pullRequest { number state } } } }""";
    // STACK_FIELDS as the fields of a line, in jq: the number of the stack, the position, the size and its pull
    // requests as position:number:state, separated by commas, all empty when it is not stacked. An entry whose pull
    // request cannot be read is left out.
    private static final String STACK_JQ = """
            def stack: [(.stack.number // "" | tostring), (.stackEntry.position // "" | tostring), \
            (.stack.size // "" | tostring), \
            ([.stack.entries.nodes[]? | select(.pullRequest != null) \
            | "\\(.position):\\(.pullRequest.number):\\(.pullRequest.state)"] | join(","))]; """;
    private static final int STACK_FIELD_COUNT = 4;
    /*
     * The open pull requests of the repository, all pages of them, with the states of the checks on their last
     * commit. gh fills in the repository of the working tree for {owner} and {repo}.
     */
    private static final String PRS_QUERY = """
            query($owner: String!, $name: String!, $endCursor: String) { repository(owner: $owner, name: $name) { \
            pullRequests(states: OPEN, first: 100, after: $endCursor) { pageInfo { hasNextPage endCursor } \
            nodes { number headRefName isDraft isCrossRepository reviewDecision %s \
            commits(last: 1) { nodes { commit { statusCheckRollup { contexts(first: 100) { nodes { __typename \
            ... on CheckRun { status conclusion } ... on StatusContext { state } } } } } } } } } } }""".formatted(STACK_FIELDS);
    /*
     * One line per pull request that is not from a fork:
     * number, head branch, whether it is a draft, its review decision, the states of its checks and the fields of
     * STACK_JQ, separated by tabs.
     */
    private static final String JQ = STACK_JQ + """
            .data.repository.pullRequests.nodes[] | select(.isCrossRepository | not) \
            | [(.number | tostring), .headRefName, (.isDraft | tostring), (.reviewDecision // ""), \
            ([.commits.nodes[0].commit.statusCheckRollup.contexts.nodes[]? | %s] | join(","))] + stack | @tsv""".formatted(CHECK_STATE);
    private static final int FIELD_COUNT = 5 + STACK_FIELD_COUNT;

    /*
     * What a list of pull requests shows of each, in GraphQL. The checks are those on its last commit.
     */
    private static final String SUMMARY_FRAGMENT = """
            fragment summary on PullRequest { number title url updatedAt isDraft isCrossRepository headRefName \
            author { login } repository { nameWithOwner } reviewDecision %s \
            commits(last: 1) { nodes { commit { statusCheckRollup { state } } } } }""".formatted(STACK_FIELDS);
    /*
     * The open pull requests of the repository, the most recently updated first, the one opened last in any state, and
     * the account gh runs as. gh fills in the repository of the working tree for {owner} and {repo}.
     */
    private static final String REPOSITORY_PRS_QUERY = """
            query($owner: String!, $name: String!) { viewer { login } \
            repository(owner: $owner, name: $name) { nameWithOwner \
            latest: pullRequests(first: 1, orderBy: {field: CREATED_AT, direction: DESC}) { nodes { number } } \
            pullRequests(states: OPEN, first: 100, orderBy: {field: UPDATED_AT, direction: DESC}) \
            { totalCount nodes { ...summary } } } } """ + SUMMARY_FRAGMENT;
    private static final String MY_PRS_LIMIT = "50";
    private static final String MY_PRS_SEARCH = "is:pr is:open archived:false sort:updated-desc ";
    /*
     * The open pull requests that ask for the review of the account gh runs as, and the ones it opened, in any
     * repository, the most recently updated first.
     */
    private static final String MY_PRS_QUERY = """
            query($reviewRequested: String!, $authored: String!, $limit: Int!) { \
            reviewRequested: search(type: ISSUE, query: $reviewRequested, first: $limit) \
            { issueCount nodes { ...summary } } \
            authored: search(type: ISSUE, query: $authored, first: $limit) { issueCount nodes { ...summary } } } \
            """ + SUMMARY_FRAGMENT;
    // A pull request of SUMMARY_FRAGMENT as the fields of a line, in jq.
    private static final String SUMMARY_FIELDS_JQ = STACK_JQ + """
            def summary: [.repository.nameWithOwner, (.number | tostring), .title, (.author.login // ""), \
            .headRefName, (.isDraft | tostring), (.isCrossRepository | tostring), (.reviewDecision // ""), \
            (.commits.nodes[0].commit.statusCheckRollup.state // ""), .updatedAt, .url] + stack; """;
    /*
     * Tab separated lines, each starting with what it is: a "list" line per list, with its name, the repository
     * it is of (empty for a search) and how many match, and a "pr" line per pull request, with the name of its
     * list and the fields of SUMMARY_FIELDS_JQ. The ones of the repository start with a "viewer" line, with the login
     * gh runs as, and a "latest" line, with the number of the pull request opened last, 0 for none.
     */
    private static final String REPOSITORY_PRS_JQ = SUMMARY_FIELDS_JQ + """
            (["viewer", .data.viewer.login] | @tsv), \
            (["latest", (.data.repository.latest.nodes[0].number // 0 | tostring)] | @tsv), \
            (.data.repository | (["list", "repository", .nameWithOwner, (.pullRequests.totalCount | tostring)] \
            | @tsv), (.pullRequests.nodes[] | (["pr", "repository"] + summary) | @tsv))""";
    private static final String MY_PRS_JQ = SUMMARY_FIELDS_JQ + """
            .data | to_entries[] | .key as $list | (["list", $list, "", (.value.issueCount | tostring)] | @tsv), \
            (.value.nodes[] | select(.number != null) | (["pr", $list] + summary) | @tsv)""";
    private static final String VIEWER_LINE = "viewer\t";
    private static final String LATEST_LINE = "latest\t";
    private static final int LIST_LINE_FIELDS = 4;
    private static final int SUMMARY_LINE_FIELDS = 13 + STACK_FIELD_COUNT;
    // The base branch of a pull request on the first line, its body after it.
    private static final String PREVIEW_JQ = ".baseRefName + \"\\n\" + .body";

    /*
     * Everything about one pull request the dialog shows, its latest commit for its checks. gh fills in the
     * repository of the working tree for {owner} and {repo}. GitHub no longer says when a commit was pushed, so the
     * latest of when it was opened, its latest commit and its latest force push tells when it was last pushed to; as
     * GitHub gives all three as UTC timestamps ending in Z, the latest of them sorts last as text.
     */
    private static final String DETAILS_QUERY = """
            query($owner: String!, $name: String!, $number: Int!) { repository(owner: $owner, name: $name) { \
            pullRequest(number: $number) { number title author { login } state isDraft headRefName baseRefName \
            headRefOid %s createdAt additions deletions changedFiles mergeable reviewDecision comments { totalCount } \
            labels(first: 100) { nodes { name } } latestReviews(first: 100) { nodes { author { login } state } } \
            reviewRequests(first: 100) { nodes { requestedReviewer { ... on User { login } ... on Bot { login } \
            ... on Mannequin { login } ... on Team { name } } } } \
            timelineItems(last: 1, itemTypes: [HEAD_REF_FORCE_PUSHED_EVENT]) { nodes { \
            ... on HeadRefForcePushedEvent { createdAt } } } \
            commits(last: 1) { totalCount nodes { commit { committedDate statusCheckRollup { contexts(first: 100) { nodes { __typename \
            ... on CheckRun { name status conclusion } ... on StatusContext { context state } } } } } } } \
            reviewThreads(last: 100) { totalCount nodes { isResolved comments { totalCount } } } } } }""".formatted(STACK_FIELDS);
    /*
     * Tab separated lines, each starting with what it is: one "pr" line with the fields of the pull request, one
     * "threads" line with how many review threads it has, how many of them were read, and how many of those are
     * unresolved and how many comments they hold, a "stack" line with the fields of STACK_JQ when it is stacked,
     * then a "label" line per label, a "review" line per latest review, a "requested" line per requested reviewer
     * and a "check" line per check, with its state as in JQ.
     */
    private static final String DETAILS_JQ = STACK_JQ + """
            .data.repository.pullRequest | \
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
            (.commits.nodes[0].commit.statusCheckRollup.contexts.nodes[]? | ["check", \
            ((if .__typename == "CheckRun" then .name else .context end) // ""), \
            %s] | @tsv))""".formatted(CHECK_STATE);
    private static final int DETAIL_FIELD_COUNT = 18;
    /*
     * How many commits base branch $base has that commit $head does not: GitHub's mergeable only says whether a pull
     * request merges without conflicts, not that its base moved on. gh fills in the repository of the working tree
     * for {owner} and {repo}.
     */
    private static final String BEHIND_QUERY = """
            query($owner: String!, $name: String!, $base: String!, $head: String!) { \
            repository(owner: $owner, name: $name) { ref(qualifiedName: $base) { compare(headRef: $head) { behindBy } } } }""";
    // The count of BEHIND_QUERY, empty when the base branch is gone.
    private static final String BEHIND_JQ = ".data.repository.ref.compare.behindBy // \"\" | tostring";

    // How a merge is doing, in jq: its status, its id while it is pending, and why when it failed.
    private static final String MERGE_JQ = "[.status, (.details.uuid // \"\"), (.details.message // \"\")] | @tsv";

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

    /*
     * The branches of the repository with the combined state of the checks on their tip, all pages of them. gh
     * fills in the repository of the working tree for {owner} and {repo}.
     */
    private static final String BRANCHES_QUERY = """
            query($owner: String!, $name: String!, $endCursor: String) { \
            repository(owner: $owner, name: $name) { nameWithOwner \
            refs(refPrefix: "refs/heads/", first: 100, after: $endCursor) { pageInfo { hasNextPage endCursor } \
            nodes { name target { ... on Commit { statusCheckRollup { state } } } } } } }""";
    /*
     * Tab separated lines, each starting with what it is: a "repo" line with the repository as owner/name per
     * page, and a "branch" line per branch with its name and the state of the checks on its tip, empty without
     * checks.
     */
    private static final String BRANCHES_JQ = """
            .data.repository | (["repo", .nameWithOwner] | @tsv), \
            (.refs.nodes[] | ["branch", .name, (.target.statusCheckRollup.state // "")] | @tsv)""";

    private static final Set<String> FAILED =
            HashSet.of("FAILURE", "ERROR", "CANCELLED", "TIMED_OUT", "ACTION_REQUIRED", "STARTUP_FAILURE");
    private static final Set<String> RUNNING = HashSet.of("PENDING", "EXPECTED", "QUEUED", "IN_PROGRESS", "WAITING");

    private static final Pattern CREATED_URL = Pattern.compile("/pull/(\\d{1,9})$");

    // The accounts gh is logged in to, by host, login, whether it is the active one and whether its token works.
    private static final String LOGINS_JQ = """
            .hosts | to_entries[] | .key as $host | .value[] \
            | [$host, .login, (.active | tostring), .state] | @tsv""";

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
     * Parses the lines {@link #LOGINS_JQ} prints.
     */
    static Either<UnexpectedOutput, List<GhLogin>> parseLogins(String raw) {
        return List.of(raw.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> List.of(line.split("\t", -1)).map(CliGitHub::unescape))
                .map(fields -> Option.when(fields.size() == 4, fields)
                        .map(f -> new GhLogin(
                                f.get(0),
                                f.get(1),
                                f.get(2).equals("true"),
                                f.get(3).equals("success")))
                        .toEither(() -> new UnexpectedOutput(fields.mkString("\t"))))
                .transform(Either::sequenceRight)
                .map(List::ofAll);
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
    static GhCommand createCommand(String head, String base, NewPullRequest pullRequest) {
        return gh(List.of(
                        "pr",
                        "create",
                        "--head",
                        head,
                        "--base",
                        base,
                        "--title",
                        pullRequest.title(),
                        "--body",
                        pullRequest.body())
                .appendAll(Option.when(pullRequest.draft(), "--draft"))
                .toJavaArray(String[]::new));
    }

    static GhCommand browseCommand(String branch) {
        return gh("browse", "--branch", branch);
    }

    /**
     * The command that has GitHub merge pull request {@code number} in the background, only while its branch is at
     * {@code head}, unless that is empty. gh fills in the repository of the working tree for {owner} and {repo}.
     */
    static GhCommand mergeCommand(int number, String head, MergeMethod method) {
        return gh(List.of(
                        "api",
                        "--method",
                        "PUT",
                        "repos/{owner}/{repo}/pulls/" + number + "/merge-async",
                        "-f",
                        "merge_method=" + mergeMethod(method))
                .appendAll(head.isEmpty() ? List.empty() : List.of("-f", "sha=" + head))
                .appendAll(List.of("--jq", MERGE_JQ))
                .toJavaArray(String[]::new));
    }

    /**
     * The command that asks GitHub how the merge {@code id} of pull request {@code number} is doing.
     */
    static GhCommand mergeStatusCommand(int number, String id) {
        return gh("api", "repos/{owner}/{repo}/pulls/" + number + "/merge-async/" + id, "--jq", MERGE_JQ);
    }

    private static String mergeMethod(MergeMethod method) {
        return switch (method) {
            case MERGE -> "merge";
            case SQUASH -> "squash";
            case REBASE -> "rebase";
        };
    }

    /**
     * Parses the line {@link #MERGE_JQ} prints.
     */
    static Either<UnexpectedOutput, MergeStatus> parseMergeStatus(String raw) {
        List<String> fields =
                List.of(raw.replaceFirst("\n\\z", "").split("\t", -1)).map(CliGitHub::unescape);
        Option<MergeStatus> status = Option.when(fields.size() == 3, fields).flatMap(f -> switch (f.get(0)) {
            case "pending" -> Option.when(!f.get(1).isEmpty(), () -> new MergeStatus.Pending(f.get(1)));
            case "merged" -> Option.of(new MergeStatus.Merged());
            case "enqueued" -> Option.of(new MergeStatus.Enqueued());
            case "failed" -> Option.of(new MergeStatus.Failed(f.get(2)));
            default -> Option.none();
        });
        return status.toEither(() -> new UnexpectedOutput(raw));
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
     * Parses the lines the jq filter of {@link #pullRequests()} prints.
     */
    static Either<UnexpectedOutput, List<PullRequest>> parse(String raw) {
        return List.of(raw.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> pullRequest(line).toEither(() -> new UnexpectedOutput(line)))
                .transform(Either::sequenceRight)
                .map(List::ofAll);
    }

    private static Option<PullRequest> pullRequest(String line) {
        List<String> fields = List.of(line.split("\t", -1));
        return Option.when(fields.size() == FIELD_COUNT, fields)
                .flatMap(f -> Try.of(() -> Integer.parseInt(f.get(0)))
                        .flatMap(number -> stack(f.drop(5))
                                .map(stack -> new PullRequest(
                                        number,
                                        f.get(1),
                                        f.get(2).equals("true"),
                                        checks(List.of(f.get(4).split(",")).filter(state -> !state.isEmpty())),
                                        review(f.get(3)),
                                        stack)))
                        .toOption());
    }

    /**
     * Parses the lines {@link #REPOSITORY_PRS_JQ} and {@link #MY_PRS_JQ} print: the lists by their names, each with
     * the repository it is of, empty for a search.
     */
    static Either<UnexpectedOutput, Map<String, Tuple2<String, PullRequestList>>> parseLists(String raw) {
        List<List<String>> lines = List.of(raw.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> List.of(line.split("\t", -1)).map(CliGitHub::unescape));
        Option<List<String>> unknown = lines.find(fields -> !knownListsLine(fields));
        if (unknown.isDefined()) {
            return Either.left(new UnexpectedOutput(unknown.get().mkString("\t")));
        }
        Map<String, List<PullRequestSummary>> byList = lines.filter(
                        fields -> fields.head().equals("pr"))
                .groupBy(fields -> fields.get(1))
                .mapValues(prs -> prs.flatMap(fields -> summary(fields.drop(2))));
        return Either.right(lines.filter(fields -> fields.head().equals("list"))
                .toLinkedMap(fields -> Tuple.of(
                        fields.get(1),
                        Tuple.of(
                                fields.get(2),
                                new PullRequestList(
                                        byList.get(fields.get(1)).getOrElse(List.empty()),
                                        Integer.parseInt(fields.get(3)))))));
    }

    private static boolean knownListsLine(List<String> fields) {
        return switch (fields.head()) {
            case "list" -> fields.size() == LIST_LINE_FIELDS && isNumber(fields.get(3));
            case "pr" ->
                fields.size() == SUMMARY_LINE_FIELDS && summary(fields.drop(2)).isDefined();
            default -> false;
        };
    }

    private static boolean isNumber(String field) {
        return Try.of(() -> Integer.parseInt(field)).isSuccess();
    }

    private static Option<PullRequestSummary> summary(List<String> f) {
        return stack(f.drop(11))
                .flatMap(stack -> Try.of(() -> new PullRequestSummary(
                        f.get(0),
                        Integer.parseInt(f.get(1)),
                        f.get(2),
                        f.get(3),
                        f.get(4),
                        f.get(5).equals("true"),
                        f.get(6).equals("true"),
                        checks(List.of(f.get(8)).filter(state -> !state.isEmpty())),
                        review(f.get(7)),
                        Instant.parse(f.get(9)),
                        f.get(10),
                        stack)))
                .toOption();
    }

    /**
     * Parses the lines the jq filter of {@link #branchChecks()} prints.
     */
    static Either<UnexpectedOutput, BranchChecks> parseBranchChecks(String raw) {
        List<List<String>> lines = List.of(raw.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> List.of(line.split("\t", -1)).map(CliGitHub::unescape));
        Option<List<String>> unknown =
                lines.find(fields -> !(fields.size() == 2 && fields.head().equals("repo")
                        || fields.size() == 3 && fields.head().equals("branch")));
        if (unknown.isDefined()) {
            return Either.left(new UnexpectedOutput(unknown.get().mkString("\t")));
        }
        return lines.find(f -> f.head().equals("repo"))
                .map(repo -> new BranchChecks(
                        repo.get(1),
                        lines.filter(f -> f.head().equals("branch"))
                                .toMap(
                                        f -> f.get(1),
                                        f -> checks(List.of(f.get(2)).filter(state -> !state.isEmpty())))))
                .toEither(() -> new UnexpectedOutput(raw));
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
        List<Check> checks = lines.filter(f -> f.head().equals("check"))
                // A check without a state yet, like a status nobody set, is neither passing nor failing.
                .map(f -> new Check(f.get(1), checks(List.of(f.get(2)).filter(state -> !state.isEmpty()))));
        Try<Option<PullRequestStack>> stack = lines.find(f -> f.head().equals("stack"))
                .map(f -> stack(f.tail()))
                .getOrElse(Try.success(Option.none()));
        return lines.find(f -> f.head().equals("pr"))
                .flatMap(f -> threads.flatMap(reviewThreads -> stack.mapTry(pullRequestStack -> new PullRequestDetails(
                                Integer.parseInt(f.get(1)),
                                f.get(2),
                                f.get(3),
                                PullRequestDetails.State.valueOf(f.get(4)),
                                f.get(5).equals("true"),
                                f.get(6),
                                f.get(7),
                                Instant.parse(f.get(8)),
                                Instant.parse(f.get(9)),
                                Integer.parseInt(f.get(10)),
                                Integer.parseInt(f.get(11)),
                                Integer.parseInt(f.get(12)),
                                Integer.parseInt(f.get(17)),
                                mergeable(f.get(13)),
                                review(f.get(14)),
                                Integer.parseInt(f.get(15)),
                                reviewThreads,
                                labels,
                                reviewers,
                                checks,
                                f.get(16),
                                pullRequestStack,
                                Option.none()))
                        .toOption()))
                .toEither(() -> new UnexpectedOutput(raw));
    }

    /**
     * Parses what {@link #BEHIND_JQ} prints: none when the base branch is gone.
     */
    static Either<UnexpectedOutput, Option<Integer>> parseBehind(String raw) {
        String count = raw.strip();
        return count.isEmpty()
                ? Either.right(Option.none())
                : Try.of(() -> Option.of(Integer.parseInt(count))).toEither(() -> new UnexpectedOutput(raw));
    }

    private static boolean known(List<String> fields) {
        return switch (fields.head()) {
            case "pr" -> fields.size() == DETAIL_FIELD_COUNT;
            case "label", "requested" -> fields.size() == 2;
            case "review", "check" -> fields.size() == 3;
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

    /**
     * The combined state of the checks with {@code states}, as {@link Checks#combined} combines them.
     */
    static Checks checks(List<String> states) {
        return Checks.combined(states.map(state ->
                FAILED.contains(state) ? Checks.FAILING : RUNNING.contains(state) ? Checks.PENDING : Checks.PASSING));
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
        return unconfigured.read(gh("auth", "status", "--json", "hosts", "--jq", LOGINS_JQ), CliGitHub::parseLogins);
    }

    @Override
    public Either<GhError, List<PullRequest>> pullRequests() {
        return process.read(
                gh(
                        "api",
                        "graphql",
                        "--paginate",
                        "-F",
                        "owner={owner}",
                        "-F",
                        "name={repo}",
                        "-f",
                        "query=" + PRS_QUERY,
                        "--jq",
                        JQ),
                CliGitHub::parse);
    }

    @Override
    public Either<GhError, RepositoryPullRequests> repositoryPullRequests() {
        return process.read(
                gh(
                        "api",
                        "graphql",
                        "-F",
                        "owner={owner}",
                        "-F",
                        "name={repo}",
                        "-f",
                        "query=" + REPOSITORY_PRS_QUERY,
                        "--jq",
                        REPOSITORY_PRS_JQ),
                CliGitHub::parseRepositoryPullRequests);
    }

    /**
     * The open pull requests of the repository from what {@link #REPOSITORY_PRS_JQ} prints: a "viewer" line with the
     * login gh runs as, a "latest" line with the number of the pull request opened last, and the lines
     * {@link #parseLists(String)} reads. Without a "latest" line, or with one that is no number, none is known: 0.
     */
    static Either<UnexpectedOutput, RepositoryPullRequests> parseRepositoryPullRequests(String raw) {
        List<String> lines = List.of(raw.split("\n"));
        Option<String> viewer = field(lines, VIEWER_LINE).filter(login -> !login.isBlank());
        int latest = field(lines, LATEST_LINE)
                .filter(CliGitHub::isNumber)
                .map(Integer::parseInt)
                .getOrElse(0);
        return parseLists(lines.filter(line -> !line.startsWith(VIEWER_LINE) && !line.startsWith(LATEST_LINE))
                        .mkString("\n"))
                .flatMap(lists -> lists.get("repository")
                        .map(list -> new RepositoryPullRequests(list._1, list._2, viewer, latest))
                        .toEither(() -> new UnexpectedOutput(raw)));
    }

    /**
     * What follows {@code start} on the first of {@code lines} that starts with it, if any.
     */
    private static Option<String> field(List<String> lines, String start) {
        return lines.find(line -> line.startsWith(start)).map(line -> unescape(line.substring(start.length())));
    }

    @Override
    public Either<GhError, MyPullRequests> myPullRequests() {
        return process.read(
                gh(
                        "api",
                        "graphql",
                        "-f",
                        "reviewRequested=" + MY_PRS_SEARCH + "review-requested:@me",
                        "-f",
                        "authored=" + MY_PRS_SEARCH + "author:@me",
                        "-F",
                        "limit=" + MY_PRS_LIMIT,
                        "-f",
                        "query=" + MY_PRS_QUERY,
                        "--jq",
                        MY_PRS_JQ),
                raw -> parseLists(raw)
                        .flatMap(lists -> lists.get("reviewRequested")
                                .flatMap(review -> lists.get("authored")
                                        .map(authored -> new MyPullRequests(review._2, authored._2)))
                                .toEither(() -> new UnexpectedOutput(raw))));
    }

    @Override
    public Either<GhError, PullRequestPreview> pullRequestPreview(String url) {
        return process.read(gh("pr", "view", url, "--json", "baseRefName,body", "--jq", PREVIEW_JQ), Either::right)
                .flatMap(described -> process.read(gh("pr", "diff", url, "--color", "never"), Either::right)
                        .map(diff -> preview(described, diff)));
    }

    /**
     * The preview of a pull request, from what {@link #PREVIEW_JQ} prints and its diff, cut off after
     * {@link #MAX_DIFF_LINES} lines.
     */
    static PullRequestPreview preview(String described, String diff) {
        int newline = described.indexOf('\n');
        String base = newline < 0 ? described.strip() : described.substring(0, newline);
        String body = newline < 0 ? "" : described.substring(newline + 1).stripTrailing();
        List<String> lines = List.of(diff.split("\n", -1));
        return lines.size() <= MAX_DIFF_LINES
                ? new PullRequestPreview(base, body, diff, false)
                : new PullRequestPreview(base, body, lines.take(MAX_DIFF_LINES).mkString("", "\n", "\n"), true);
    }

    @Override
    public Either<GhError, BranchChecks> branchChecks() {
        return process.read(
                gh(
                        "api",
                        "graphql",
                        "--paginate",
                        "-F",
                        "owner={owner}",
                        "-F",
                        "name={repo}",
                        "-f",
                        "query=" + BRANCHES_QUERY,
                        "--jq",
                        BRANCHES_JQ),
                CliGitHub::parseBranchChecks);
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
                .map(details -> details.state() == PullRequestDetails.State.OPEN
                                && !details.head().isEmpty()
                        ? behindBase(details.baseBranch(), details.head())
                                .map(details::withBehindBase)
                                .getOrElse(details)
                        : details);
    }

    /**
     * How many commits base branch {@code base} has that commit {@code head} does not; none when that cannot be read,
     * also when the read fails, as the rest of the details read fine and come back without it.
     */
    private Option<Integer> behindBase(String base, String head) {
        return process.read(
                        gh(
                                "api",
                                "graphql",
                                "-F",
                                "owner={owner}",
                                "-F",
                                "name={repo}",
                                "-f",
                                "base=refs/heads/" + base,
                                "-f",
                                "head=" + head,
                                "-f",
                                "query=" + BEHIND_QUERY,
                                "--jq",
                                BEHIND_JQ),
                        CliGitHub::parseBehind)
                .toOption()
                .flatMap(behind -> behind);
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
    public Either<GhFailure, Created> createPullRequest(String head, String base, NewPullRequest pullRequest) {
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
    public Either<GhFailure, List<ExecutedCommand>> browse(String branch) {
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
    public Either<GhFailure, Merging> mergePullRequest(int number, String head, MergeMethod method) {
        return process.run(mergeCommand(number, head, method))
                .flatMap(output -> parseMergeStatus(output.stdout())
                        .map(status -> new Merging(status, output.log()))
                        .mapLeft(unexpected -> new GhFailure(unexpected, output.log())));
    }

    @Override
    public Either<GhError, MergeStatus> mergeStatus(int number, String id) {
        return process.read(mergeStatusCommand(number, id), CliGitHub::parseMergeStatus);
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
