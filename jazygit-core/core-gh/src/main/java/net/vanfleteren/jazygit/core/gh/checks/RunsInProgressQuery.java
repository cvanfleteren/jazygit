package net.vanfleteren.jazygit.core.gh.checks;

import static net.vanfleteren.jazygit.core.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.integer;

import net.vanfleteren.jazygit.core.gh.process.GhCommand;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * How many workflow runs of the repository are in progress, over REST, as the builds screen counts them (ADR 212).
 * gh fills in the repository of the working tree for {owner} and {repo}.
 */
final class RunsInProgressQuery {

    // One run is enough: total_count counts them all.
    static final GhCommand COMMAND = gh("api", "repos/{owner}/{repo}/actions/runs?status=in_progress&per_page=1");

    static final Decoder<Integer> DECODER = field("total_count", integer());

    private RunsInProgressQuery() {}
}
