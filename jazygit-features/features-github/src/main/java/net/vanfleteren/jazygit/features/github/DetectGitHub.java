package net.vanfleteren.jazygit.features.github;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.model.GhLogin;
import net.vanfleteren.jazygit.core.repo.PullRequestHome;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Finds out whether GitHub applies to the repository (ADR 171): it does when the remote its pull requests are opened
 * against, its home (ADR 177), is on a GitHub host, and gh is found at {@code executable}; the home is the remote
 * {@code gh repo set-default} chose, if it was run, as gh reads pull requests from it. A host is GitHub's when it says so, as github.com and an enterprise's
 * {@code .ghe.com} do, or when gh is logged in to it, as to a GitHub Enterprise Server; no remote is taken to be on
 * github.com. It runs in the background, as it runs gh.
 */
public record DetectGitHub(String executable) implements Cmd {

    // What gh keeps on the remote set-default chose: remote.<name>.gh-resolved is base.
    private static final String RESOLVED = "gh-resolved";

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
        Map<RemoteName, String> urls = env.git().remotes().urls().getOrElse(HashMap::empty);
        Set<String> hosts = PullRequestHome.of(base(env), urls.keySet())
                .flatMap(urls::get)
                .flatMap(GhAccount::hostOf)
                .map(host -> host.toLowerCase(Locale.ROOT))
                .toSet();
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
     * The remote {@code gh repo set-default} chose, which gh marks {@code gh-resolved} {@code base}; none when it was
     * not run, or the remotes could not be read.
     */
    private static Option<RemoteName> base(Env env) {
        return env.git()
                .remotes()
                .setting(RESOLVED)
                .toOption()
                .flatMap(resolved -> resolved.find(remote -> remote._2().equals("base")))
                .map(remote -> remote._1());
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
