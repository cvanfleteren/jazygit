package net.vanfleteren.jazygit.core.gh.checks;

import static net.vanfleteren.jazygit.core.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.lib.json.Decoder.at;
import static net.vanfleteren.jazygit.lib.json.Decoder.byField;
import static net.vanfleteren.jazygit.lib.json.Decoder.fail;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.list;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.nullable;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;
import static net.vanfleteren.jazygit.lib.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.graphql.CheckFragment;
import net.vanfleteren.jazygit.core.gh.graphql.SuiteFragment;
import net.vanfleteren.jazygit.core.gh.model.BranchChecks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.process.GhCommand;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * The branches of the repository, all pages of them, each with the state of the checks on its tip (ADR 42).
 */
final class BranchesQuery {

    /*
     * The branches with the combined state of the checks on their tip, and its check suites. gh fills in the
     * repository of the working tree for {owner} and {repo}, and $endCursor while it reads the pages.
     */
    static final String QUERY = """
            query($owner: String!, $name: String!, $endCursor: String) { \
            repository(owner: $owner, name: $name) { nameWithOwner \
            refs(refPrefix: "refs/heads/", first: 100, after: $endCursor) { pageInfo { hasNextPage endCursor } \
            nodes { name target { __typename ... on Commit { statusCheckRollup { state } %s } } } } } }""".formatted(SuiteFragment.FIELDS);

    // All pages, as one array.
    static final GhCommand COMMAND = gh(
            "api",
            "graphql",
            "--paginate",
            "--slurp",
            "-F",
            "owner={owner}",
            "-F",
            "name={repo}",
            "-f",
            "query=" + QUERY);

    /*
     * The state of the checks on a tip: that of their rollup, none without checks, and pending while a workflow run
     * on it waits to start, unless they failed. A tip that is no commit has no checks.
     */
    static final Decoder<Checks> TIP = byField(
            "__typename",
            HashMap.of(
                    "Commit",
                    map2(
                            field("statusCheckRollup", nullable(field("state", string()))),
                            SuiteFragment.WAITING,
                            (state, waiting) -> CheckFragment.checks(state.toList()
                                    .filter(value -> !value.isEmpty())
                                    .appendAll(Option.when(!waiting.isEmpty(), "PENDING"))))),
            succeed(Checks.NONE));

    // A page: the repository, and its branches with the state of their checks.
    static final Decoder<Tuple2<String, List<Tuple2<ForgeBranch, Checks>>>> PAGE = at(
            "data.repository",
            map2(
                    field("nameWithOwner", string()),
                    at(
                            "refs.nodes",
                            list(map2(field("name", string()).map(ForgeBranch::new), field("target", TIP), Tuple::of))),
                    Tuple::of));

    static final Decoder<BranchChecks> DECODER = list(PAGE)
            .flatMap(pages -> pages.headOption()
                    .fold(
                            () -> fail("expected a page"),
                            first -> succeed(new BranchChecks(
                                    first._1(), pages.flatMap(Tuple2::_2).toMap(branch -> branch)))));

    private BranchesQuery() {}
}
