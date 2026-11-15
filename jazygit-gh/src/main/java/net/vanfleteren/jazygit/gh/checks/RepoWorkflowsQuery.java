package net.vanfleteren.jazygit.gh.checks;

import static net.vanfleteren.jazygit.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map4;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.gh.process.GhCommand;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The workflows of the repository, over REST: GraphQL has no list of them (ADR 42, 131).
 */
final class RepoWorkflowsQuery {

    // gh fills in the repository of the working tree for {owner} and {repo}.
    static final GhCommand COMMAND = gh("api", "repos/{owner}/{repo}/actions/workflows?per_page=100");

    static final Decoder<List<RepoWorkflow>> DECODER = field(
            "workflows",
            list(map4(
                    field("node_id", string()),
                    field("name", string()),
                    field("path", string()),
                    field("state", string()),
                    (id, name, path, state) -> new RepoWorkflow(id, name, path, state.equals("active")))));

    private RepoWorkflowsQuery() {}
}
