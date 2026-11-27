package net.vanfleteren.jazygit.core.gh.checks;

import static net.vanfleteren.jazygit.core.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.integer;

import net.vanfleteren.jazygit.core.gh.process.GhCommand;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * The workflow runs of the repository, over REST, as the builds screen counts and opens them (ADR 212). gh fills in
 * the repository of the working tree for {owner} and {repo}.
 */
final class RunsInProgressQuery {

    // One run is enough: total_count counts them all.
    static final GhCommand COMMAND = gh("api", "repos/{owner}/{repo}/actions/runs?status=in_progress&per_page=1");

    static final Decoder<Integer> DECODER = field("total_count", integer());

    private RunsInProgressQuery() {}

    /**
     * The command that opens the workflow run {@code runId} in the browser, on the host gh knows the repository on.
     */
    static GhCommand open(long runId) {
        return gh("run", "view", String.valueOf(runId), "--web");
    }
}
