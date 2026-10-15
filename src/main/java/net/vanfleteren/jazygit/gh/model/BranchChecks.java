package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.Map;
import java.util.Locale;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;

/**
 * How the CI checks are doing on the tip of each branch of a GitHub repository.
 *
 * @param repository the repository, as {@code owner/name}
 * @param checks     by the name of the branch
 */
public record BranchChecks(String repository, Map<String, Checks> checks) {

    /**
     * Whether the git remote {@code url} points at this repository, over ssh or https, with or without
     * {@code .git}. Only the path is compared, so a host alias from the ssh config matches too.
     */
    public boolean isAt(String url) {
        String path = url.strip().toLowerCase(Locale.ROOT).replaceAll("/+$", "").replaceAll("\\.git$", "");
        String name = repository.toLowerCase(Locale.ROOT);
        return path.endsWith("/" + name) || path.endsWith(":" + name);
    }
}
