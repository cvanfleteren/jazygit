package net.vanfleteren.jazygit.gh.graphql;

import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.graphql.CheckFragment.WorkflowRun;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The check suites of a commit, in GraphQL and as they are decoded, to find its workflow runs that wait to start, as
 * those have no check run yet (ADR 35, 42).
 */
public final class SuiteFragment {

    // The check suites of a commit, in GraphQL.
    public static final String FIELDS = """
            checkSuites(first: 20) { nodes { status checkRuns { totalCount } \
            workflowRun { databaseId createdAt runAttempt event workflow { name } } } }""";

    /**
     * The workflow runs of a commit of {@link #FIELDS} that wait to start, each as a queued check named after its
     * workflow and without an id of its own.
     */
    public static final Decoder<List<Check>> WAITING =
            waiting(CheckFragment.WORKFLOW_RUN.map(Option::some)).map(runs -> runs.map(SuiteFragment::queued));

    private SuiteFragment() {}

    /*
     * The workflow run of a suite that waits to start: of a workflow, not completed, without a check run; one whose
     * check runs GitHub does not give does not wait. A suite of another app has no workflow run: one that never adds
     * a check run stays queued for good.
     */
    private static <T> Decoder<Option<T>> suite(Decoder<Option<T>> run) {
        return map3(
                field("status", string()),
                field("checkRuns", nullable(field("totalCount", integer()))),
                field("workflowRun", nullable(run)),
                (status, checkRuns, waiting) -> waiting.flatMap(read -> read)
                        .filter(waits -> !status.equals("COMPLETED") && checkRuns.contains(0)));
    }

    /**
     * The workflow runs of a commit of {@link #FIELDS}, or of fields asking more of each run, that wait to start, each
     * as {@code run} decodes it; one it gives none for is left out.
     */
    public static <T> Decoder<List<T>> waiting(Decoder<Option<T>> run) {
        return field("checkSuites", nullable(field("nodes", list(suite(run)))))
                .map(suites -> suites.toList().flatMap(nodes -> nodes).flatMap(waits -> waits));
    }

    private static Check queued(WorkflowRun run) {
        return new Check(
                run.workflow(),
                Checks.PENDING,
                run.started()
                        .map(started -> new CheckRun(
                                0,
                                run.workflow(),
                                "",
                                Option.none(),
                                Option.none(),
                                started.id(),
                                started.createdAt(),
                                started.attempt(),
                                started.event())));
    }
}
