package net.vanfleteren.jazygit.core.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

class BaseRemoteTest {

    private static RemoteName named(String name) {
        return new RemoteName(name);
    }

    private static Set<RemoteName> remotes(String... names) {
        return HashSet.of(names).map(RemoteName::new);
    }

    @Test
    void theRemoteTheForgeOpensPullRequestsAgainstIsTheBase() {
        assertEquals(
                Option.of(named("upstream")),
                BaseRemote.of(Option.of(named("upstream")), Option.of(named("origin")), remotes("origin", "upstream")));
    }

    @Test
    void aForgeRemoteThatIsNoRemoteIsNotTaken() {
        assertEquals(
                Option.of(named("origin")),
                BaseRemote.of(Option.of(named("gone")), Option.none(), remotes("origin", "upstream")));
    }

    @Test
    void withoutAForgeTheCheckoutDefaultRemoteIsTheBase() {
        assertEquals(
                Option.of(named("upstream")),
                BaseRemote.of(Option.none(), Option.of(named("upstream")), remotes("origin", "upstream")));
    }

    @Test
    void theOnlyRemoteIsTheBaseWhateverItIsCalled() {
        assertEquals(Option.of(named("github")), BaseRemote.of(Option.none(), Option.none(), remotes("github")));
    }

    @Test
    void amongSeveralRemotesOriginIsTheBaseWhenThereIsOne() {
        assertEquals(
                Option.of(named("origin")), BaseRemote.of(Option.none(), Option.none(), remotes("mirror", "origin")));
    }

    @Test
    void amongSeveralRemotesWithoutOriginThereIsNoBase() {
        assertEquals(Option.none(), BaseRemote.of(Option.none(), Option.none(), remotes("mirror", "github")));
        assertEquals(Option.none(), BaseRemote.of(Option.none(), Option.none(), remotes()));
    }

    @Test
    void aBranchIsPushedToItsOwnRemoteElseThePushDefaultElseTheBase() {
        BranchName branch = new BranchName("feature");
        RemoteSetup setup = new RemoteSetup(
                Option.of(named("upstream")),
                Option.of(named("fork")),
                HashMap.of(new BranchName("other"), named("mine")));

        assertEquals(Option.of(named("fork")), setup.pushRemote(branch));
        assertEquals(Option.of(named("mine")), setup.pushRemote(new BranchName("other")));
        assertEquals(
                Option.of(named("upstream")),
                new RemoteSetup(setup.base(), Option.none(), setup.pushRemotes()).pushRemote(branch));
        assertEquals(Option.none(), RemoteSetup.NONE.pushRemote(branch));
    }
}
