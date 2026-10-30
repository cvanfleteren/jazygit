package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.nullable;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * How many commits base branch $base has that commit $head does not: GitHub's mergeable only says whether a pull
 * request merges without conflicts, not that its base moved on (ADR 42).
 */
final class BehindQuery {

    /*
     * gh fills in the repository of the working tree for {owner} and {repo}.
     */
    static final String QUERY = """
            query($owner: String!, $name: String!, $base: String!, $head: String!) { \
            repository(owner: $owner, name: $name) { ref(qualifiedName: $base) { compare(headRef: $head) { behindBy } } } }""";

    // The count, none when the base branch is gone.
    static final Decoder<Option<Integer>> DECODER = at(
                    "data.repository.ref", nullable(field("compare", nullable(field("behindBy", nullable(integer()))))))
            .map(ref -> ref.flatMap(compare -> compare).flatMap(behind -> behind));

    private BehindQuery() {}

    static GhCommand command(String base, GhCommit head) {
        return gh(
                "api",
                "graphql",
                "-F",
                "owner={owner}",
                "-F",
                "name={repo}",
                "-f",
                "base=refs/heads/" + base,
                "-f",
                "head=" + head.oid(),
                "-f",
                "query=" + QUERY);
    }
}
