package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * The remote whose repository the pull requests of a repository are opened against (ADR 177), whichever forge it is
 * on: the one set for it, else {@code upstream}, the project a fork was made of, else {@code origin}, else the only
 * remote. gh picks the repository it reads pull requests of in the same order.
 */
public final class PullRequestHome {

    public static final RemoteName UPSTREAM = new RemoteName("upstream");

    private PullRequestHome() {}

    /**
     * The home among {@code remotes}, with {@code set} the one set for it, if any; none without remotes, or among
     * several with none set and none named {@code upstream} or {@code origin}.
     */
    public static Option<RemoteName> of(Option<RemoteName> set, Set<RemoteName> remotes) {
        return set.filter(remotes::contains)
                .orElse(() -> Option.when(remotes.contains(UPSTREAM), UPSTREAM))
                .orElse(() -> Option.when(remotes.contains(RemoteBranch.DEFAULT_REMOTE), RemoteBranch.DEFAULT_REMOTE))
                .orElse(() -> Option.when(remotes.size() == 1, remotes::head));
    }
}
