package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * The rule that picks the remote whose repository the pull requests of a repository are opened against, among those
 * of one forge (ADR 177): the one set for it, else {@code upstream}, the project a fork was made of, else
 * {@code github}, else {@code origin}, else the only one. It is gh's order of the names; unlike gh, it does not
 * itself leave out the remotes on no forge, as the forges tell which are theirs.
 */
public final class PullRequestHome {

    public static final RemoteName UPSTREAM = new RemoteName("upstream");

    private static final List<RemoteName> NAMED =
            List.of(UPSTREAM, new RemoteName("github"), RemoteBranch.DEFAULT_REMOTE);

    private PullRequestHome() {}

    /**
     * The home among {@code remotes}, with {@code set} the one set for it, if any; none without remotes, or among
     * several with none set and none named {@code upstream}, {@code github} or {@code origin}.
     */
    public static Option<RemoteName> of(Option<RemoteName> set, Set<RemoteName> remotes) {
        return set.filter(remotes::contains)
                .orElse(() -> NAMED.find(remotes::contains))
                .orElse(() -> Option.when(remotes.size() == 1, remotes::head));
    }
}
