package net.vanfleteren.jazygit.app;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.core.git.CliGit;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.process.SshAsking;
import net.vanfleteren.jazygit.kernel.settings.RecentRepositories;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsPaths;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Repositories;
import net.vanfleteren.jazygit.kernel.state.Terminal;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * What commands run against for a repository on disk: git, and what each feature builds for it (ADR 171). It names no
 * forge: a feature that talks to one gives git the credentials it talks to the remotes with.
 */
final class RepositoryEnv {

    private RepositoryEnv() {}

    /**
     * What commands run against for the repository {@code git} reads, with the {@code settings} of the session kept in
     * {@code store}: what every feature builds for it, and git with the credentials they give; ssh, run for what the
     * user starts, may ask the user as {@code ssh} lets it (ADR 150).
     */
    static Env of(
            Git git,
            Settings settings,
            SettingsStore store,
            RecentRepositories recent,
            Repositories repositories,
            Terminal terminal,
            SshAsking ssh) {
        Feature.RepositoryOpening opening = new Feature.RepositoryOpening(
                git,
                settings,
                store,
                ssh,
                SettingsPaths.stateFile(),
                SettingsPaths.repositories(),
                SettingsPaths.cacheDir());
        List<Feature.Contribution> contributions =
                Features.ALL.map(feature -> feature.running().of(opening));
        List<Supplier<Map<String, String>>> credentials =
                contributions.flatMap(contribution -> contribution.gitCredentials());
        return contributions.foldLeft(
                new Env(
                        credentials.isEmpty()
                                ? CliGit.withCredentials(git, HashMap::empty, ssh)
                                : CliGit.withCredentials(
                                        git,
                                        () -> credentials.foldLeft(
                                                HashMap.<String, String>empty(), (all, each) -> all.merge(each.get())),
                                        ssh),
                        store,
                        recent,
                        repositories,
                        terminal),
                (env, contribution) -> env.with(contribution.services()));
    }

    /**
     * As {@code env}, but for the repository {@code git} reads, with the {@code settings} of the session, where ssh
     * may ask the user as {@code ssh} lets it.
     */
    static Env reopen(Env env, Git git, Settings settings, SshAsking ssh) {
        return of(git, settings, env.settings(), env.recent(), env.repositories(), env.terminal(), ssh);
    }
}
