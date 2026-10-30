package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.BranchCheckRuns;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The checks on the tips of branches, with how each ran, each branch in an alias b0, b1, … of its own (ADR 42).
 */
final class BranchCheckRunsQuery {

    /*
     * The checks on the tips of branches, each in an alias b0, b1, … whose branch is in the variable of that name.
     * gh fills in the repository of the working tree for {owner} and {repo}.
     */
    private static final String QUERY = """
            query($owner: String!, $name: String!%s) { repository(owner: $owner, name: $name) { nameWithOwner %s } }""";
    private static final String REF = """
            b%1$d: ref(qualifiedName: $b%1$d) { name target { __typename ... on Commit { statusCheckRollup { \
            contexts(first: 100) { nodes { %2$s } } } %3$s } } }""";

    // The checks on a tip, and the workflow runs that wait to start on it; none on a tip that is no commit.
    private static final Decoder<List<Check>> TIP = byField(
            "__typename",
            HashMap.of(
                    "Commit",
                    map2(
                            field("statusCheckRollup", nullable(at("contexts.nodes", list(CheckFragment.CHECK)))),
                            SuiteFragment.WAITING,
                            (checks, waiting) -> checks.getOrElse(List.empty()).appendAll(waiting))),
            succeed(List.empty()));
    // A branch by its name with its checks; none when it is gone.
    private static final Decoder<Option<Tuple2<String, List<Check>>>> BRANCH =
            nullable(map2(field("name", string()), field("target", TIP), Tuple::of));

    private BranchCheckRunsQuery() {}

    /**
     * The query for {@code count} branches.
     */
    static String query(int count) {
        List<Integer> aliases = List.range(0, count);
        return QUERY.formatted(
                aliases.map(i -> ", $b" + i + ": String!").mkString(),
                aliases.map(i -> REF.formatted(i, CheckFragment.FIELDS, SuiteFragment.FIELDS))
                        .mkString(" "));
    }

    /**
     * The command that reads the checks of {@code branches}, each by its full ref in the variable of its alias.
     */
    static GhCommand command(List<String> branches) {
        return gh(List.of("api", "graphql", "-F", "owner={owner}", "-F", "name={repo}")
                .appendAll(branches.zipWithIndex()
                        .flatMap(branch -> List.of("-f", "b" + branch._2() + "=refs/heads/" + branch._1())))
                .appendAll(List.of("-f", "query=" + query(branches.size())))
                .toJavaArray(String[]::new));
    }

    /**
     * What GitHub answers the query for {@code count} branches: the repository, and the checks of each branch that is
     * there and has any.
     */
    static Decoder<BranchCheckRuns> decoder(int count) {
        Decoder<List<Option<Tuple2<String, List<Check>>>>> branches = List.range(0, count)
                .foldLeft(succeed(List.empty()), (read, i) -> map2(read, field("b" + i, BRANCH), List::append));
        return at(
                "data.repository",
                map2(
                        field("nameWithOwner", string()),
                        branches,
                        (repository, found) -> new BranchCheckRuns(
                                repository,
                                found.flatMap(branch -> branch)
                                        .filter(branch -> !branch._2().isEmpty())
                                        .toMap(branch -> branch))));
    }
}
