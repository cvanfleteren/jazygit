package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * Parsing of {@code git for-each-ref} and {@code git log} output.
 */
class CliParseTest {

    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);

    private static String ref(String... fields) {
        return String.join("\u001f", fields) + "\n";
    }

    /**
     * A remote-tracking ref as {@code for-each-ref} lists it with the fields of the branches: no upstream, HEAD
     * or worktree.
     */
    private static String remote(String name, String sha, String time, String symref) {
        return ref(name, sha, time, "", "", "", "", symref, "", "");
    }

    /**
     * One entry of a reflog as {@code git log --walk-reflogs} lists it.
     */
    private static String reflog(String ref, long time, String subject) {
        return ref + "@{" + time + "}\u001f" + subject + "\n";
    }

    @Test
    void remoteBranchesAreTheMostRecentlyCommittedToFirstWithoutTheHeadOfARemote() {
        String raw = remote("refs/remotes/origin/HEAD", A, "300", "refs/remotes/origin/trunk")
                + remote("refs/remotes/origin/trunk", A, "300", "")
                + remote("refs/remotes/upstream/feature/nested", B, "400", "")
                + remote("refs/remotes/origin/old", B, "100", "")
                + ref("refs/heads/main", A, "500", "*", "", "", "", "", "", "");

        assertEquals(
                List.of(
                        new RemoteRef(
                                new RemoteBranch("upstream", new BranchName("feature/nested")),
                                new Sha(B),
                                Instant.ofEpochSecond(400)),
                        new RemoteRef(
                                new RemoteBranch("origin", new BranchName("trunk")),
                                new Sha(A),
                                Instant.ofEpochSecond(300)),
                        new RemoteRef(
                                new RemoteBranch("origin", new BranchName("old")),
                                new Sha(B),
                                Instant.ofEpochSecond(100))),
                CliBranch.parseAll(raw, HashMap.empty()).get().remote());
        assertEquals(
                List.of(new BranchName("main")),
                CliBranch.parseAll(raw, HashMap.empty()).get().local().map(Branch::name));
    }

    @Test
    void aRemoteConfiguredWithALeadingDashIsNoUpstreamButOneWithASpaceIs() {
        String raw = ref("refs/heads/odd", A, "300", "*", "-oProxyCommand=x", "refs/heads/odd", "", "", "", "")
                + ref("refs/heads/path", B, "200", " ", "/repos/my repo", "refs/heads/path", "", "", "", "");

        List<Branch> local = CliBranch.parseAll(raw, HashMap.empty()).get().local();

        assertEquals(
                List.of(Option.none(), Option.of(new RemoteBranch("/repos/my repo", new BranchName("path")))),
                local.map(Branch::remoteUpstream));
    }

    @Test
    void branchesKnowTheirUpstreamRemoteAndWhetherTheyAreTheDefault() {
        String raw = ref(
                        "refs/heads/feature",
                        B,
                        "200",
                        " ",
                        "origin",
                        "refs/heads/feature",
                        "[ahead 1, behind 2]",
                        "",
                        "refs/remotes/origin/feature",
                        "")
                + ref(
                        "refs/heads/gone",
                        B,
                        "100",
                        " ",
                        "origin",
                        "refs/heads/gone",
                        "[gone]",
                        "",
                        "refs/remotes/origin/gone",
                        "")
                + ref(
                        "refs/heads/trunk",
                        A,
                        "300",
                        "*",
                        "origin",
                        "refs/heads/trunk",
                        "",
                        "",
                        "refs/remotes/origin/trunk",
                        "")
                + ref("refs/remotes/origin/HEAD", A, "300", " ", "", "", "", "refs/remotes/origin/trunk", "", "")
                + ref("refs/remotes/origin/feature", B, "200", " ", "", "", "", "", "", "")
                + ref("refs/remotes/origin/trunk", A, "300", " ", "", "", "", "", "", "");

        assertEquals(
                List.of(
                        Fixtures.branch(
                                new BranchName("trunk"),
                                true,
                                new Sha(A),
                                Instant.ofEpochSecond(300),
                                true,
                                true,
                                Option.of(new Upstream(
                                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("trunk"))),
                                        0,
                                        0))),
                        Fixtures.branch(
                                new BranchName("feature"),
                                false,
                                new Sha(B),
                                Instant.ofEpochSecond(200),
                                true,
                                false,
                                Option.of(new Upstream(
                                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))),
                                        1,
                                        2))),
                        Fixtures.branch(
                                new BranchName("gone"),
                                false,
                                new Sha(B),
                                Instant.ofEpochSecond(100),
                                false,
                                false,
                                new Tracking.Gone(
                                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("gone")))))),
                CliBranch.parseAll(raw, HashMap.empty()).get().local());
    }

    @Test
    void aBranchIsOnItsRemoteWhenItsUpstreamIsEvenUnderAnotherName() {
        String raw = ref(
                        "refs/heads/local",
                        A,
                        "1",
                        "*",
                        "origin",
                        "refs/heads/elsewhere",
                        "",
                        "",
                        "refs/remotes/origin/elsewhere",
                        "")
                + ref("refs/remotes/origin/elsewhere", A, "1", " ", "", "", "", "", "", "");

        assertEquals(
                List.of(true),
                CliBranch.parseAll(raw, HashMap.empty()).get().local().map(Branch::hasRemote));
    }

    @Test
    void aBranchThatFollowsALocalOneIsOnItsRemoteWhenOriginHasItsName() {
        String raw = ref("refs/heads/main", A, "2", " ", "", "", "", "", "", "")
                + ref("refs/heads/feature", B, "1", "*", ".", "refs/heads/main", "", "", "refs/heads/main", "")
                + ref("refs/remotes/origin/feature", B, "1", " ", "", "", "", "", "", "");

        Branch feature = CliBranch.parseAll(raw, HashMap.empty())
                .get()
                .local()
                .find(branch -> branch.name().equals(new BranchName("feature")))
                .get();
        assertEquals(new RemoteBranch("origin", new BranchName("feature")), feature.pushTarget());
        assertEquals(List.of(true, true), List.of(feature.hasRemote(), feature.remoteDeletable()));
    }

    @Test
    void anUpstreamKeepsItsRemoteApartFromItsBranchEvenWhenTheRemoteNameHasASlash() {
        String raw = ref(
                "refs/heads/local",
                A,
                "1",
                "*",
                "my/fork",
                "refs/heads/feature/x",
                "",
                "",
                "refs/remotes/my/fork/feature/x",
                "");

        Branch local = CliBranch.parseAll(raw, HashMap.empty()).get().local().head();
        assertEquals(
                Option.of(new BranchRef.Remote(new RemoteBranch("my/fork", new BranchName("feature/x")))),
                local.upstream().map(Upstream::branch));
        assertEquals(new RemoteBranch("my/fork", new BranchName("feature/x")), local.pushTarget());
    }

    @Test
    void aBranchKnowsTheOtherWorktreeItIsCheckedOutInButNotThisOne() {
        String raw = ref("refs/heads/main", A, "2", "*", "", "", "", "", "", "/repos/main")
                + ref("refs/heads/linked", A, "1", " ", "", "", "", "", "", "/repos/linked dir")
                + ref("refs/heads/free", A, "0", " ", "", "", "", "", "", "");

        assertEquals(
                List.of(Option.none(), Option.of(java.nio.file.Path.of("/repos/linked dir")), Option.none()),
                CliBranch.parseAll(raw, HashMap.empty()).get().local().map(Branch::worktree));
    }

    @Test
    void withoutOriginHeadMainOrMasterIsTheDefault() {
        String raw = ref("refs/heads/master", A, "1", "*", "", "", "", "", "", "");

        assertEquals(
                List.of(true),
                CliBranch.parseAll(raw, HashMap.empty()).get().local().map(Branch::isDefault));
    }

    @Test
    void commitsKeepTheirParentsSubjectAndBody() {
        String raw = String.join(
                        "\u001f",
                        A,
                        B,
                        "Ada",
                        "ada@example.com",
                        "60",
                        "Subject",
                        "HEAD -> main, tag: v1, tag: v1.1, origin/main",
                        "Subject\n\nBody\nmore\n")
                + "\0" + String.join("\u001f", B, "", "Bob", "bob@example.com", "0", "Root", "", "Root\n");

        assertEquals(
                List.of(
                        new Commit(
                                new Sha(A),
                                "Ada",
                                "ada@example.com",
                                Instant.ofEpochSecond(60),
                                "Subject",
                                "Body\nmore",
                                List.of(new Sha(B)),
                                List.of(new Tag("v1"), new Tag("v1.1"))),
                        Fixtures.commit(new Sha(B), "Bob", "bob@example.com", Instant.EPOCH, "Root", "", List.empty())),
                CliLog.parse(raw).get());
    }

    @Test
    void aBranchIsMadeWhenItsOldestReflogEntrySaysGitCreatedIt() {
        String raw = reflog("refs/heads/fresh", 50, "branch: Created from main")
                + reflog("refs/heads/feature/a@b", 100, "commit: Work")
                + reflog("refs/heads/feature/a@b", 50, "branch: Created from HEAD")
                + reflog("refs/heads/renamed", 80, "Branch: renamed refs/heads/old to refs/heads/renamed")
                + reflog("refs/heads/renamed", 60, "branch: Created from main")
                + reflog("refs/heads/main", 90, "pull: Fast-forward")
                + reflog("refs/heads/main", 70, "clone: from https://example.com/repo.git")
                + reflog("refs/heads/expired", 40, "reset: moving to HEAD~1");

        assertEquals(
                HashMap.of(
                        new BranchName("fresh"), Instant.ofEpochSecond(50),
                        new BranchName("feature/a@b"), Instant.ofEpochSecond(50),
                        new BranchName("renamed"), Instant.ofEpochSecond(60)),
                CliBranch.parseCreated(raw).get());
    }

    @Test
    void aBranchIsAsOldAsWhenItWasMadeUntilItIsCommittedToAfterwards() {
        String raw = ref("refs/heads/fresh", A, "10", " ", "", "", "", "", "", "")
                + ref("refs/heads/worked", A, "100", " ", "", "", "", "", "", "")
                + ref("refs/heads/unknown", A, "20", " ", "", "", "", "", "", "");
        HashMap<BranchName, Instant> created = HashMap.of(
                new BranchName("fresh"), Instant.ofEpochSecond(50),
                new BranchName("worked"), Instant.ofEpochSecond(50));

        assertEquals(
                List.of(
                        Tuple.of("worked", Instant.ofEpochSecond(100)),
                        Tuple.of("fresh", Instant.ofEpochSecond(50)),
                        Tuple.of("unknown", Instant.ofEpochSecond(20))),
                CliBranch.parseAll(raw, created)
                        .get()
                        .local()
                        .map(b -> Tuple.of(b.name().value(), b.lastActivity())));
    }

    @Test
    void malformedOutputIsAnErrorRatherThanAnException() {
        assertEquals(
                new GitError.UnexpectedOutput("refs/heads/main\u001f" + A),
                CliBranch.parseAll(ref("refs/heads/main", A), HashMap.empty()).getLeft());
        assertEquals(
                new GitError.UnexpectedOutput("soon"),
                CliBranch.parseAll(ref("refs/heads/main", A, "soon", "*", "", "", "", "", "", ""), HashMap.empty())
                        .getLeft());
        assertEquals(
                new GitError.UnexpectedOutput("refs/heads/main@{soon}\u001fcommit: A"),
                CliBranch.parseCreated("refs/heads/main@{soon}\u001fcommit: A\n")
                        .getLeft());
        assertEquals(
                new GitError.UnexpectedOutput("refs/heads/main\u001fcommit: A"),
                CliBranch.parseCreated("refs/heads/main\u001fcommit: A\n").getLeft());
        assertEquals(
                new GitError.UnexpectedOutput(" "),
                CliLog.parse(String.join("\u001f", " ", "", "Ada", "ada@example.com", "60", "S", "", "S\n"))
                        .getLeft());
        assertEquals(
                new GitError.UnexpectedOutput("stash@{0} without a separator"),
                CliStash.parse("stash@{0} without a separator\n").getLeft());
        assertEquals(
                List.of(
                        new Stash(
                                "stash@{0}",
                                new Sha(A), "work", Option.of(new BranchName("main")), Instant.ofEpochSecond(60)),
                        new Stash(
                                "stash@{1}",
                                new Sha(B),
                                "WIP on feature/x: abc1234 Add widget",
                                Option.of(new BranchName("feature/x")),
                                Instant.ofEpochSecond(61))),
                CliStash.parse("stash@{0}\u001f" + A + "\u001fOn main: work\u001f60\n" + "stash@{1}\u001f" + B
                                + "\u001fWIP on feature/x: abc1234 Add widget\u001f61\n")
                        .get());
        // On a detached HEAD, git names no branch but says so in its place.
        assertEquals(
                List.of(
                        new Stash("stash@{0}", new Sha(A), "work", Option.none(), Instant.ofEpochSecond(60)),
                        new Stash(
                                "stash@{1}",
                                new Sha(B),
                                "WIP on (no branch): abc1234 Add widget",
                                Option.none(),
                                Instant.ofEpochSecond(61))),
                CliStash.parse("stash@{0}\u001f" + A + "\u001fOn (no branch): work\u001f60\n" + "stash@{1}\u001f" + B
                                + "\u001fWIP on (no branch): abc1234 Add widget\u001f61\n")
                        .get());
        assertEquals(
                new GitError.UnexpectedOutput("1 M"),
                CliStatus.parse("# branch.head main\0001 M\0").getLeft());
    }

    @Test
    void aShaIsNeitherBlankNorSpaced() {
        assertEquals(Option.of(new Sha(A)), Sha.parse(" " + A + "\n"));
        assertEquals(Option.none(), Sha.parse("  "));
        assertEquals(Option.none(), Sha.parse(A + " " + B));
    }

    @Test
    void theParentsOfTheCommitsAreReadAsFullShasOnly() {
        assertEquals(
                List.of(List.of(new Sha(A), new Sha(B)), List.of(new Sha(B))),
                CliLog.parents(A + " " + B + "\n" + B + "\n").get());
        assertTrue(CliLog.parents(A + " " + B + "\nwarning: something\n").isLeft());
    }

    @Test
    void aShaIsAFullObjectId() {
        assertEquals(Option.of(new Sha(A)), Sha.parse(A));
        assertEquals(Option.of(new Sha("a".repeat(64))), Sha.parse("a".repeat(64)));
        // An abbreviated one would lose to a branch or tag of the same name, as 4711 would.
        assertEquals(Option.none(), Sha.parse("4711"));
        assertEquals(Option.none(), Sha.parse("a".repeat(39)));
        assertEquals(Option.none(), Sha.parse("a".repeat(41)));
        assertEquals(Option.none(), Sha.parse("-" + "a".repeat(39)));
        assertEquals(Option.none(), Sha.parse("main"));
    }
}
