package net.vanfleteren.jazygit.core.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The remote the pull requests of a repository are opened against (ADR 177).
 */
class PullRequestHomeTest {

    private static Set<RemoteName> remotes(String... names) {
        return HashSet.of(names).map(RemoteName::new);
    }

    private static Option<RemoteName> named(String name) {
        return Option.of(new RemoteName(name));
    }

    @Test
    void theRemoteSetForItIsTheHome() {
        assertEquals(named("mine"), PullRequestHome.of(named("mine"), remotes("origin", "upstream", "mine")));
    }

    @Test
    void aRemoteSetThatIsGoneIsNot() {
        assertEquals(named("origin"), PullRequestHome.of(named("gone"), remotes("origin")));
    }

    @Test
    void elseTheProjectAForkWasMadeOfThenGithubThenOrigin() {
        assertEquals(named("upstream"), PullRequestHome.of(Option.none(), remotes("origin", "upstream", "github")));
        assertEquals(named("github"), PullRequestHome.of(Option.none(), remotes("origin", "github")));
        assertEquals(named("origin"), PullRequestHome.of(Option.none(), remotes("origin", "mirror")));
    }

    @Test
    void elseTheOnlyRemoteAndNoneAmongSeveral() {
        assertEquals(named("codeberg"), PullRequestHome.of(Option.none(), remotes("codeberg")));
        assertEquals(Option.none(), PullRequestHome.of(Option.none(), remotes("codeberg", "mirror")));
        assertEquals(Option.none(), PullRequestHome.of(Option.none(), remotes()));
    }
}
