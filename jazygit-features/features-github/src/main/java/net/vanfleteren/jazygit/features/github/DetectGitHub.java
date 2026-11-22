package net.vanfleteren.jazygit.features.github;

import io.vavr.Lazy;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeClaim;

/**
 * Finds out which remotes of the repository are on GitHub (ADR 171, 177), so that the kernel decides whether GitHub
 * is its forge: those on a GitHub host, with gh found at {@code executable}; none without gh. A host is GitHub's when
 * it says so, as github.com and an enterprise's {@code .ghe.com} do, or when gh is logged in to it, as to a GitHub
 * Enterprise Server. The remote {@code gh repo set-default} chose is the one GitHub prefers as the home. It runs in the
 * background, as it runs gh.
 */
public record DetectGitHub(String executable) implements Cmd {

    // The forge, as the user is shown it when asked which forge a repository is on (ADR 177).
    private static final String NAME = "GitHub";

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
        GitHub gh = GitHubServices.of(env);
        if (urls.isEmpty() || gh.account().version(executable).isLeft()) {
            return new FeatureMsg.Claimed(ForgeClaim.none(GitHubFeature.ID, NAME));
        }
        // gh checks the token of each host it is logged in to over the network: only asked for a host that does not
        // say it is GitHub's, as one of a GitHub Enterprise Server.
        Lazy<Set<String>> logins = Lazy.of(() -> gh.account()
                .logins()
                .getOrElse(List.empty())
                .map(login -> login.host().toLowerCase(Locale.ROOT))
                .toSet());
        Map<RemoteName, String> claimed = LinkedHashMap.ofEntries(urls.toList()
                .sortBy(remote -> remote._1().value())
                .filter(remote -> GhAccount.hostOf(remote._2())
                        .map(host -> host.toLowerCase(Locale.ROOT))
                        .exists(host -> named(host) || logins.get().contains(host))));
        return new FeatureMsg.Claimed(new ForgeClaim(GitHubFeature.ID, NAME, claimed, HashSet.empty(), base(env)));
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
        return new FeatureMsg.Claimed(ForgeClaim.none(GitHubFeature.ID, NAME));
    }
}
