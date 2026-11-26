package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.Tuple;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Env;

/**
 * The repository on Forgejo the pull requests are opened against, their home (ADR 177), and the remotes on the same
 * host, which their branches may be in (ADR 176).
 *
 * @param home    the repository of the home
 * @param remotes the repository of each remote on the host of the home, the home's included
 */
public record ForgejoRemotes(ForgejoRepository home, Map<RemoteName, ForgejoRepository> remotes) {

    /**
     * The home, {@code home} as the kernel decided it (ADR 177), and the remotes on its host, when the home is on
     * Forgejo; none when it is not, or the remotes could not be listed.
     */
    public static Option<ForgejoRemotes> of(Env env, Forgejo forgejo, RemoteName home) {
        return env.git()
                .remotes()
                .urls()
                .toOption()
                .flatMap(urls -> urls.get(home)
                        .flatMap(forgejo::repository)
                        .filter(forgejo::isForgejo)
                        .map(repositoryOfHome -> new ForgejoRemotes(
                                repositoryOfHome,
                                urls.flatMap((remote, url) -> forgejo.repository(url)
                                        .filter(repository -> repository.base().equals(repositoryOfHome.base()))
                                        .map(repository -> Tuple.of(remote, repository))))));
    }

    /**
     * The branch of a remote that {@code pullRequest} merges: its branch, in the remote whose repository it is in;
     * none when that is not a remote's, as a fork of someone else's.
     */
    Option<RemoteBranch> head(ForgejoPullRequest pullRequest) {
        return pullRequest
                .headRepository()
                .flatMap(full -> remotes.find(remote -> named(remote._2()).equals(full.toLowerCase(Locale.ROOT))))
                .map(remote -> new RemoteBranch(
                        remote._1(), new BranchName(pullRequest.head().name())));
    }

    private static String named(ForgejoRepository repository) {
        return (repository.owner() + "/" + repository.name()).toLowerCase(Locale.ROOT);
    }
}
