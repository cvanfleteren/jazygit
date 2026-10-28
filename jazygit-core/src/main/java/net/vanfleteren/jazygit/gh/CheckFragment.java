package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.instant;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.longValue;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.map4;
import static net.vanfleteren.jazygit.json.Decoder.map5;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * A check on a commit, in GraphQL and as it is decoded (ADR 42): a check run, of a GitHub Actions workflow or of
 * another app, or a commit status. Its nodes are of a union, so each asks for its {@code __typename}.
 */
final class CheckFragment {

    // What there is to know of a check run of a workflow and when it ran, in GraphQL (ADR 32).
    static final String CHECK_RUN_FIELDS = """
            databaseId name status conclusion startedAt completedAt \
            checkSuite { workflowRun { databaseId createdAt runAttempt event workflow { name } } }""";
    // A check with its name, its state and how it ran, in GraphQL.
    static final String FIELDS =
            "__typename ... on CheckRun { " + CHECK_RUN_FIELDS + " } ... on StatusContext { context state }";
    // A check with only its state, in GraphQL.
    static final String STATE_FIELDS =
            "__typename ... on CheckRun { status conclusion } ... on StatusContext { state }";

    /**
     * The workflow run of a check suite or a check run: its name, empty when it has none, and when and how it was
     * started, none when that cannot be read.
     */
    static final Decoder<WorkflowRun> WORKFLOW_RUN = map2(
            field("workflow", nullable(field("name", nullable(string()))))
                    .map(workflow -> workflow.flatMap(name -> name).getOrElse("")),
            orNone(map4(
                    field("databaseId", longValue()),
                    field("createdAt", instant()),
                    field("runAttempt", integer()),
                    field("event", nullable(string())),
                    (id, createdAt, attempt, event) ->
                            new WorkflowRun.Started(id, createdAt, attempt, event.getOrElse("")))),
            WorkflowRun::new);

    /**
     * The state of a check as GitHub says it, of {@link #STATE_FIELDS}: a check run that is not completed yet is
     * PENDING, else its conclusion; a commit status gives its state; empty for neither, or what is not known yet.
     */
    static final Decoder<String> STATE = byField(
            "__typename",
            HashMap.of(
                    "CheckRun",
                    map2(field("status", string()), field("conclusion", nullable(string())), CheckFragment::runState),
                    "StatusContext",
                    field("state", nullable(string())).map(state -> state.getOrElse(""))),
            succeed(""));

    // How a check run of a workflow ran, none for another app's or when it cannot be read.
    static final Decoder<Option<CheckRun>> RUN = orNone(map5(
                    field("databaseId", longValue()),
                    field("conclusion", nullable(string())),
                    field("startedAt", nullable(instant())),
                    field("completedAt", nullable(instant())),
                    at("checkSuite.workflowRun", nullable(WORKFLOW_RUN)),
                    (id, conclusion, startedAt, completedAt, workflowRun) -> workflowRun.flatMap(run -> run.started()
                            .map(started -> new CheckRun(
                                    id,
                                    run.workflow(),
                                    conclusion.getOrElse(""),
                                    startedAt,
                                    completedAt,
                                    started.id(),
                                    started.createdAt(),
                                    started.attempt(),
                                    started.event())))))
            .map(run -> run.flatMap(checkRun -> checkRun));

    /**
     * A check of {@link #FIELDS}: a check run with how it ran, a commit status, or, of a type not asked for, a check
     * without a name or a state.
     */
    static final Decoder<Check> CHECK = byField(
            "__typename",
            HashMap.of(
                    "CheckRun",
                    map3(
                            field("name", nullable(string())),
                            STATE,
                            RUN,
                            (name, state, run) -> new Check(name.getOrElse(""), state(state), run)),
                    "StatusContext",
                    map2(
                            field("context", nullable(string())),
                            STATE,
                            (context, state) -> new Check(context.getOrElse(""), state(state)))),
            succeed(new Check("", Checks.NONE)));

    private static final Set<String> FAILED =
            HashSet.of("FAILURE", "ERROR", "CANCELLED", "TIMED_OUT", "ACTION_REQUIRED", "STARTUP_FAILURE");
    private static final Set<String> RUNNING = HashSet.of("PENDING", "EXPECTED", "QUEUED", "IN_PROGRESS", "WAITING");

    private CheckFragment() {}

    /**
     * The states of checks combined: one failed fails them all, else one running keeps them pending.
     */
    static Checks checks(List<String> states) {
        return Checks.combined(states.map(state ->
                FAILED.contains(state) ? Checks.FAILING : RUNNING.contains(state) ? Checks.PENDING : Checks.PASSING));
    }

    /**
     * The state of one check; a check without a state yet, like a status nobody set, is neither passing nor failing.
     */
    static Checks state(String state) {
        return checks(List.of(state).filter(value -> !value.isEmpty()));
    }

    private static String runState(String status, Option<String> conclusion) {
        return status.equals("COMPLETED") ? conclusion.getOrElse("") : "PENDING";
    }

    /**
     * What {@code decoder} decodes, none when it cannot: for when a check ran, which is shown when it can be read and
     * left out when it cannot, as the check itself still counts (ADR 32). Nothing else is read this leniently.
     */
    private static <T> Decoder<Option<T>> orNone(Decoder<T> decoder) {
        return json -> Either.right(decoder.decode(json).toOption());
    }

    /**
     * The workflow run of a check suite or a check run.
     *
     * @param workflow the name of its workflow, empty when it has none
     * @param started  when and how it was started; none when that cannot be read
     */
    record WorkflowRun(String workflow, Option<Started> started) {

        /**
         * When and how a workflow run was started.
         *
         * @param id        its id
         * @param createdAt when it was created: of a re-run, when its first attempt was
         * @param attempt   which attempt it is, from 1
         * @param event     what started it, like {@code push}; empty when GitHub does not say
         */
        record Started(long id, Instant createdAt, int attempt, String event) {}
    }
}
