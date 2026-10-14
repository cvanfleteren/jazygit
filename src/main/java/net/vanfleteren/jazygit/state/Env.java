package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.gh.GitHub;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.settings.SettingsStore;

/**
 * What a {@link Cmd} runs against: the repository through git, its GitHub repository through gh, and where the
 * settings are kept.
 */
public record Env(GitInfoProvider git, GitHub gitHub, SettingsStore settings) {

    /**
     * Against git and gh, keeping no settings.
     */
    public Env(GitInfoProvider git, GitHub gitHub) {
        this(git, gitHub, SettingsStore.NONE);
    }
}
