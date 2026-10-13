package net.vanfleteren.jazygit.gh;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.gh.GhError.UnexpectedOutput;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

/**
 * {@link GitHub} through the {@code gh} command line tool, for the repository in a working tree. gh finds the
 * GitHub repository from the git remotes itself.
 */
public final class CliGitHub implements GitHub {

    private static final String LIMIT = "200";
    private static final String FIELDS = "number,headRefName,isDraft,isCrossRepository,reviewDecision,statusCheckRollup";
    /*
     * One line per pull request that is not from a fork:
     * number, head branch, whether it is a draft, its review decision and the states of its checks, separated
     * by tabs. A check run that is not completed yet is PENDING; a commit status gives its state.
     */
    private static final String JQ = ".[] | select(.isCrossRepository | not)"
            + " | [(.number | tostring), .headRefName, (.isDraft | tostring), (.reviewDecision // \"\"),"
            + " ([.statusCheckRollup[]? | if .__typename == \"CheckRun\""
            + " then (if .status == \"COMPLETED\" then (.conclusion // \"\") else \"PENDING\" end)"
            + " else (.state // \"\") end] | join(\",\"))] | @tsv";
    private static final int FIELD_COUNT = 5;

    private static final Set<String> FAILED = HashSet.of(
            "FAILURE", "ERROR", "CANCELLED", "TIMED_OUT", "ACTION_REQUIRED", "STARTUP_FAILURE");
    private static final Set<String> RUNNING = HashSet.of("PENDING", "EXPECTED", "QUEUED", "IN_PROGRESS", "WAITING");

    private final Path workTree;

    public CliGitHub(Path workTree) {
        this.workTree = workTree;
    }

    @Override
    public Either<GhError, List<PullRequest>> pullRequests() {
        return GhProcess.read(workTree,
                gh("pr", "list", "--state", "open", "--limit", LIMIT, "--json", FIELDS, "--jq", JQ),
                CliGitHub::parse);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> createPullRequest(String head, String base) {
        return GhProcess.log(workTree, gh("pr", "create", "--head", head, "--base", base, "--fill"));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number) {
        return GhProcess.log(workTree, gh("pr", "view", String.valueOf(number), "--web"));
    }

    /**
     * Parses the lines the jq filter of {@link #pullRequests()} prints.
     */
    static Either<UnexpectedOutput, List<PullRequest>> parse(String raw) {
        return List.of(raw.split("\\R"))
                .filter(line -> !line.isBlank())
                .map(line -> pullRequest(line).toEither(() -> new UnexpectedOutput(line)))
                .transform(Either::sequenceRight)
                .map(List::ofAll);
    }

    private static Option<PullRequest> pullRequest(String line) {
        List<String> fields = List.of(line.split("\t", -1));
        return Option.when(fields.size() == FIELD_COUNT, fields)
                .flatMap(f -> Try.of(() -> Integer.parseInt(f.get(0))).toOption()
                        .map(number -> new PullRequest(number, f.get(1), f.get(2).equals("true"),
                                checks(List.of(f.get(4).split(",")).filter(state -> !state.isEmpty())),
                                review(f.get(3)))));
    }

    /**
     * The combined state of the checks with {@code states}: failing when one failed, else pending when one is
     * still running, else passing when there are any.
     */
    static Checks checks(List<String> states) {
        return states.exists(FAILED::contains) ? Checks.FAILING
                : states.exists(RUNNING::contains) ? Checks.PENDING
                : states.isEmpty() ? Checks.NONE
                : Checks.PASSING;
    }

    static Review review(String decision) {
        return switch (decision) {
            case "APPROVED" -> Review.APPROVED;
            case "CHANGES_REQUESTED" -> Review.CHANGES_REQUESTED;
            case "REVIEW_REQUIRED" -> Review.REQUIRED;
            default -> Review.NONE;
        };
    }
}
