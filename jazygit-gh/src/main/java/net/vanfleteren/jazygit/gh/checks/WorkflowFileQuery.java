package net.vanfleteren.jazygit.gh.checks;

import static net.vanfleteren.jazygit.gh.process.GhCommand.gh;

import io.vavr.collection.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.vanfleteren.jazygit.gh.process.GhCommand;
import net.vanfleteren.jazygit.process.Limit;

/**
 * The file of a workflow at a commit, as GitHub has it, over REST: for a build on a commit that is not fetched, as one
 * of a pull request merged and deleted since (ADR 138). gh fills in the repository of the working tree for
 * {owner} and {repo}.
 */
final class WorkflowFileQuery {

    // A workflow is a page of YAML; more than this is no workflow to draw.
    static final Limit LIMIT = new Limit(20_000, 1_000_000);

    private WorkflowFileQuery() {}

    /**
     * The command that prints the file at {@code path} in the commit {@code sha}, as it is, not as JSON.
     */
    static GhCommand command(String sha, String path) {
        String encoded = List.of(path.split("/"))
                .map(segment ->
                        URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .mkString("/");
        return gh(
                "api",
                "-H",
                "Accept: application/vnd.github.raw",
                "repos/{owner}/{repo}/contents/" + encoded + "?ref=" + URLEncoder.encode(sha, StandardCharsets.UTF_8));
    }
}
