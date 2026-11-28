package net.vanfleteren.jazygit.core.git.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.*;
import org.junit.jupiter.api.Test;

/**
 * The base remote of the branches read from {@code for-each-ref} and the git configuration (ADR 215).
 */
class BaseRemoteParseTest {

    private static final String A = "a".repeat(40);
    private static final RemoteName ORIGIN = new RemoteName("origin");
    private static final RemoteName UPSTREAM = new RemoteName("upstream");
    private static final RemoteName GITHUB = new RemoteName("github");

    private static String ref(String... fields) {
        return String.join("\u001f", fields) + "\n";
    }

    private static String remote(String name, String symref) {
        return ref(name, A, "1", "", "", "", "", symref, "", "");
    }

    private static String local(String name) {
        return ref("refs/heads/" + name, A, "1", " ", "", "", "", "", "", "");
    }

    /**
     * A fork: {@code origin} is the fork, whose {@code HEAD} is {@code develop}; {@code upstream} is the project.
     */
    private static final String FORK = local("main")
            + local("develop")
            + remote("refs/remotes/origin/HEAD", "refs/remotes/origin/develop")
            + remote("refs/remotes/origin/develop", "")
            + remote("refs/remotes/upstream/HEAD", "refs/remotes/upstream/main")
            + remote("refs/remotes/upstream/main", "");

    private static AllBranches read(String raw, RemoteConfig config, Option<RemoteName> preferred) {
        return CliBranches.parseAll(raw, HashMap.empty(), HashMap.empty(), Option.none(), config, preferred)
                .get();
    }

    private static List<BranchName> defaults(AllBranches branches) {
        return branches.local().filter(Branch::isDefault).map(Branch::name);
    }

    @Test
    void inAForkWithoutAForgeTheMainBranchIsTheOneOfOrigin() {
        AllBranches branches = read(FORK, RemoteConfig.NONE, Option.none());

        assertEquals(Option.of(ORIGIN), branches.setup().base());
        assertEquals(List.of(new BranchName("develop")), defaults(branches));
    }

    @Test
    void inAForkTheRemoteTheForgeOpensPullRequestsAgainstIsTheBase() {
        AllBranches branches = read(FORK, RemoteConfig.NONE, Option.of(UPSTREAM));

        assertEquals(Option.of(UPSTREAM), branches.setup().base());
        assertEquals(List.of(new BranchName("main")), defaults(branches));
        assertEquals(
                HashMap.of(
                        new BranchRef.Local(new BranchName("main")),
                        new Sha(A),
                        new BranchRef.Local(new BranchName("develop")),
                        new Sha(A),
                        new BranchRef.Remote(new RemoteBranch(UPSTREAM, new BranchName("main"))),
                        new Sha(A)),
                branches.tips());
        assertEquals(HashSet.of(new BranchName("main")), branches.onBase());
    }

    @Test
    void theCheckoutDefaultRemoteIsTheBaseWhenNoForgeSaysOtherwise() {
        RemoteConfig config = new RemoteConfig(HashSet.empty(), Option.of(UPSTREAM), Option.none(), HashMap.empty());

        assertEquals(
                Option.of(UPSTREAM), read(FORK, config, Option.none()).setup().base());
    }

    @Test
    void aRepositoryWhoseOnlyRemoteIsNotCalledOriginHasItsDefaultBranchAndPushesThere() {
        String raw = local("trunk")
                + local("feature")
                + remote("refs/remotes/github/HEAD", "refs/remotes/github/trunk")
                + remote("refs/remotes/github/trunk", "");

        AllBranches branches = read(raw, RemoteConfig.NONE, Option.none());

        assertEquals(Option.of(GITHUB), branches.setup().base());
        assertEquals(List.of(new BranchName("trunk")), defaults(branches));
        Branch feature =
                branches.local().find(b -> b.name().value().equals("feature")).get();
        assertEquals(new RemoteBranch(GITHUB, new BranchName("feature")), feature.pushTarget());
    }

    @Test
    void aConfiguredRemoteWithoutRefsIsStillTheOnlyRemote() {
        RemoteConfig config = new RemoteConfig(HashSet.of(GITHUB), Option.none(), Option.none(), HashMap.empty());

        Branch feature = read(local("feature"), config, Option.none()).local().head();

        assertEquals(new RemoteBranch(GITHUB, new BranchName("feature")), feature.pushTarget());
    }

    @Test
    void aBranchWithoutAnUpstreamIsPushedToItsPushRemoteThenThePushDefaultThenTheBase() {
        RemoteConfig config = new RemoteConfig(
                HashSet.of(ORIGIN, UPSTREAM),
                Option.none(),
                Option.of(UPSTREAM),
                HashMap.of(new BranchName("mine"), ORIGIN));
        AllBranches withDefault = read(local("mine") + local("other"), config, Option.none());

        assertEquals(
                HashMap.of(
                        new BranchName("mine"),
                        new RemoteBranch(ORIGIN, new BranchName("mine")),
                        new BranchName("other"),
                        new RemoteBranch(UPSTREAM, new BranchName("other"))),
                HashMap.ofEntries(withDefault.local().map(b -> io.vavr.Tuple.of(b.name(), b.pushTarget()))));
    }

    @Test
    void aBranchIsOnItsRemoteWhenThatIsWhereItIsPushedTo() {
        String raw =
                local("feature") + remote("refs/remotes/upstream/feature", "") + remote("refs/remotes/origin/main", "");

        assertEquals(
                List.of(true),
                read(raw, RemoteConfig.NONE, Option.of(UPSTREAM)).local().map(Branch::hasRemote));
        assertEquals(
                List.of(false),
                read(raw, RemoteConfig.NONE, Option.of(ORIGIN)).local().map(Branch::hasRemote));
    }

    @Test
    void theConfigurationNamesTheRemotesAndWhereBranchesArePushed() {
        RemoteConfig config = RemoteConfig.parse("""
                remote.origin.url git@example.com:me/project.git
                remote.upstream.url https://example.com/project.git
                remote.upstream.pushurl https://example.com/push.git
                remote.pushdefault origin
                checkout.defaultremote upstream
                branch.feature/x.pushremote upstream
                """);

        assertEquals(HashSet.of(ORIGIN, UPSTREAM), config.remotes());
        assertEquals(Option.of(UPSTREAM), config.checkoutDefault());
        assertEquals(Option.of(ORIGIN), config.pushDefault());
        assertEquals(HashMap.of(new BranchName("feature/x"), UPSTREAM), config.pushRemotes());
    }
}
