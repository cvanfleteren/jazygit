package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.CheckFragment.WorkflowRun;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The check suites of a commit, in GraphQL and as they are decoded, to find its workflow runs that wait to start, as
 * those have no check run yet (ADR 35, 41).
 */
final class SuiteFragment {

    // The check suites of a commit, in GraphQL.
    static final String FIELDS = """
            checkSuites(first: 20) { nodes { status checkRuns { totalCount } \
            workflowRun { databaseId createdAt runAttempt event workflow { name } } } }""";

    /*
     * The workflow run of a suite that waits to start: of a workflow, not completed, without a check run. A suite of
     * another app has no workflow run: one that never adds a check run stays queued for good.
     */
    static final Decoder<Option<WorkflowRun>> SUITE = map3(
            field("status", string()),
            at("checkRuns.totalCount", integer()),
            field("workflowRun", nullable(CheckFragment.WORKFLOW_RUN)),
            (status, checkRuns, run) -> run.filter(waits -> !status.equals("COMPLETED") && checkRuns == 0));

    /**
     * The workflow runs of a commit of {@link #FIELDS} that wait to start, each as a queued check named after its
     * workflow and without an id of its own.
     */
    static final Decoder<List<Check>> WAITING = field("checkSuites", nullable(field("nodes", list(SUITE))))
            .map(suites ->
                    suites.toList().flatMap(nodes -> nodes).flatMap(run -> run).map(SuiteFragment::queued));

    private SuiteFragment() {}

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
