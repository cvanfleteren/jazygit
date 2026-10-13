package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.gh.GitHub;
import net.vanfleteren.jazygit.git.GitInfoProvider;

/**
 * What a {@link Cmd} runs against: the repository through git, and its GitHub repository through gh.
 */
public record Env(GitInfoProvider git, GitHub gitHub) {
}
