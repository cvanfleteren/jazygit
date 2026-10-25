package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.gh.GitHub;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.settings.CheckTimingsStore;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.UnseenPullRequestsStore;

/**
 * What a {@link Cmd} runs against: the repository through git, its GitHub repository through gh, where the
 * settings, the recent repositories, the unseen pull requests and how long the checks took are kept, where another
 * repository is opened, and the terminal the app is shown on.
 */
public record Env(
        GitInfoProvider git,
        GitHub gitHub,
        SettingsStore settings,
        RecentRepositories recent,
        UnseenPullRequestsStore unseen,
        CheckTimingsStore checkTimings,
        Repositories repositories,
        Terminal terminal) {

    /**
     * As given, keeping no check timings.
     */
    public Env(
            GitInfoProvider git,
            GitHub gitHub,
            SettingsStore settings,
            RecentRepositories recent,
            UnseenPullRequestsStore unseen,
            Repositories repositories,
            Terminal terminal) {
        this(git, gitHub, settings, recent, unseen, CheckTimingsStore.NONE, repositories, terminal);
    }

    /**
     * As given, keeping no unseen pull requests.
     */
    public Env(
            GitInfoProvider git,
            GitHub gitHub,
            SettingsStore settings,
            RecentRepositories recent,
            Repositories repositories,
            Terminal terminal) {
        this(git, gitHub, settings, recent, UnseenPullRequestsStore.NONE, repositories, terminal);
    }

    /**
     * As given, on no terminal.
     */
    public Env(
            GitInfoProvider git,
            GitHub gitHub,
            SettingsStore settings,
            RecentRepositories recent,
            Repositories repositories) {
        this(git, gitHub, settings, recent, repositories, Terminal.NONE);
    }

    /**
     * Against git and gh, keeping no settings and no recent repositories, and opening no other repository.
     */
    public Env(GitInfoProvider git, GitHub gitHub) {
        this(git, gitHub, SettingsStore.NONE);
    }

    /**
     * Against git and gh, keeping the settings in {@code settings}, keeping no recent repositories, and opening
     * no other repository.
     */
    public Env(GitInfoProvider git, GitHub gitHub, SettingsStore settings) {
        this(git, gitHub, settings, RecentRepositories.NONE, Repositories.NONE);
    }
}
