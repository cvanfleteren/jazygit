package net.vanfleteren.jazygit.features.github;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import java.util.Locale;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.model.GhLogin;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Finds out whether GitHub applies to the repository (ADR 171): it does with a remote on a GitHub host, and gh found
 * at {@code executable}. A host is GitHub's when it says so, as github.com and an enterprise's
 * {@code .ghe.com} do, or when gh is logged in to it, as to a GitHub Enterprise Server; no remote is taken to be on
 * github.com. It runs in the background, as it runs gh.
 */
public record DetectGitHub(String executable) implements Cmd {

    /**
     * The detection in {@code model}, with the gh its settings name.
     */
    public static DetectGitHub in(Model model) {
        return new DetectGitHub(GitHubSettings.gh(model.preferences().settings()));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Set<String> hosts = env.git()
                .remotes()
                .urls()
                .toOption()
                .map(urls -> urls.values()
                        .flatMap(GhAccount::hostOf)
                        .map(host -> host.toLowerCase(Locale.ROOT))
                        .toSet())
                .getOrElse(io.vavr.collection.HashSet::empty);
        GitHub gh = GitHubServices.of(env);
        boolean found = !hosts.isEmpty() && gh.account().version(executable).isRight();
        return new FeatureMsg.Applies(
                GitHubFeature.ID,
                found
                        && (hosts.exists(DetectGitHub::named)
                                || gh.account()
                                        .logins()
                                        .getOrElse(List.empty())
                                        .map(GhLogin::host)
                                        .exists(host -> hosts.contains(host.toLowerCase(Locale.ROOT)))));
    }

    /**
     * Whether {@code host} says it is GitHub's.
     */
    private static boolean named(String host) {
        return host.contains("github") || host.endsWith(".ghe.com");
    }

    @Override
    public Msg failed(Failure failure) {
        return new FeatureMsg.Applies(GitHubFeature.ID, false);
    }
}
