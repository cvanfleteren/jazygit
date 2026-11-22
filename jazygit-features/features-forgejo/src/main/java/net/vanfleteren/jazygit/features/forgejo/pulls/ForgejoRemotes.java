package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.Tuple;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Env;

/**
 * The remotes of the repository that are on Forgejo, and the repository each is there (ADR 174, 176).
 */
final class ForgejoRemotes {

    private ForgejoRemotes() {}

    /**
     * The repository on Forgejo of each remote that is on it; none when the remotes could not be listed.
     */
    static Option<Map<RemoteName, ForgejoRepository>> of(Env env, Forgejo forgejo) {
        return env.git()
                .remotes()
                .urls()
                .toOption()
                .map(urls -> urls.flatMap((remote, url) -> forgejo.repository(url)
                        .filter(forgejo::isForgejo)
                        .map(repository -> Tuple.of(remote, repository))));
    }
}
