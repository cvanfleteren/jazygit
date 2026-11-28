package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * The rule that picks the base remote of a repository (ADR 215): the remote whose default branch is the main branch,
 * the one branches are compared with and the one a branch without an upstream is pushed to, unless git is told another
 * for it. It is the remote the forge put the pull requests on ({@link PullRequestHome}), else the one git is
 * configured to check out from ({@code checkout.defaultRemote}), else the only remote, else {@code origin} when there
 * is one, else none. A remote that is not among the remotes is never picked.
 */
public final class BaseRemote {

    private BaseRemote() {}

    /**
     * The base remote among {@code remotes}.
     *
     * @param forge      the remote the forge of the repository opens pull requests against, if a forge is on
     * @param configured {@code checkout.defaultRemote}
     */
    public static Option<RemoteName> of(
            Option<RemoteName> forge, Option<RemoteName> configured, Set<RemoteName> remotes) {
        return forge.filter(remotes::contains)
                .orElse(() -> configured.filter(remotes::contains))
                .orElse(() -> Option.when(remotes.size() == 1, remotes::head))
                .orElse(() -> Option.of(RemoteBranch.DEFAULT_REMOTE).filter(remotes::contains));
    }
}
