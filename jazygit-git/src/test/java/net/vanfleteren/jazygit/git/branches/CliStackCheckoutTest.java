package net.vanfleteren.jazygit.git.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.CaughtUpBranch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Fetching the branches of a stack, then making and fast-forwarding them, all of them or none (ADR 99).
 */
class CliStackCheckoutTest {

    private static final RemoteName ORIGIN = new RemoteName("origin");
    private static final BranchName A = new BranchName("feat/a");
    private static final BranchName B = new BranchName("feat/b");

    @TempDir
    Path dir;

    private Path origin;
    private Path theirs;
    private Path repo;

    private static String git(Path in, String... args) throws Exception {
        java.util.List<String> command =
                new java.util.ArrayList<>(java.util.List.of("git", "-c", "user.name=t", "-c", "user.email=t@t"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(in.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
                .trim();
    }

    // origin has main, feat/a on it and feat/b on feat/a; the repository has main and feat/a, one commit behind.
    @BeforeEach
    void stack() throws Exception {
        origin = dir.resolve("origin.git");
        theirs = dir.resolve("theirs");
        repo = dir.resolve("repo");
        git(dir, "init", "-q", "--bare", "-b", "main", origin.toString());
        git(dir, "clone", "-q", origin.toString(), theirs.toString());
        git(theirs, "commit", "-q", "--allow-empty", "-m", "base");
        git(theirs, "checkout", "-q", "-b", A.value());
        git(theirs, "commit", "-q", "--allow-empty", "-m", "a1");
        git(theirs, "push", "-q", "origin", "main", A.value());
        git(dir, "clone", "-q", origin.toString(), repo.toString());
        git(repo, "branch", "-q", "--track", A.value(), "origin/" + A.value());
        git(theirs, "commit", "-q", "--allow-empty", "-m", "a2");
        git(theirs, "checkout", "-q", "-b", B.value());
        git(theirs, "commit", "-q", "--allow-empty", "-m", "b1");
        git(theirs, "push", "-q", "origin", A.value(), B.value());
    }

    private static CaughtUpBranch caughtUp(BranchName branch, boolean made, Option<BranchName> parent) {
        return new CaughtUpBranch(branch, new RemoteBranch(ORIGIN, branch), made, parent);
    }

    private CliRemotes remotes() {
        return new CliRemotes(repo, HashMap::empty);
    }

    private CliBranches branches() {
        return new CliBranches(repo);
    }

    @Test
    void theBranchesAreFetchedThenMadeAndFastForwardedFollowingTheirBranchOnTheRemote() throws Exception {
        assertTrue(remotes().fetchBranches(ORIGIN, List.of(A, B)).isRight());
        assertTrue(branches()
                .catchUp(List.of(caughtUp(A, false, Option.none()), caughtUp(B, true, Option.of(A))))
                .isRight());

        assertEquals(git(theirs, "rev-parse", A.value()), git(repo, "rev-parse", A.value()));
        assertEquals(git(theirs, "rev-parse", B.value()), git(repo, "rev-parse", B.value()));
        assertEquals("origin/" + B.value(), git(repo, "rev-parse", "--abbrev-ref", B.value() + "@{upstream}"));
        assertEquals(A.value(), git(repo, "config", "branch." + B.value() + ".gh-merge-base"));
        // Nothing was checked out: main is still where the clone left it.
        assertEquals("main", git(repo, "branch", "--show-current"));
    }

    @Test
    void noBranchMovesWhenOneIsNoFastForward() throws Exception {
        String before = git(repo, "rev-parse", A.value());
        git(repo, "checkout", "-q", A.value());
        git(repo, "commit", "-q", "--allow-empty", "-m", "mine");
        git(repo, "checkout", "-q", "main");
        String mine = git(repo, "rev-parse", A.value());
        assertTrue(remotes().fetchBranches(ORIGIN, List.of(A, B)).isRight());

        assertTrue(branches()
                .catchUp(List.of(caughtUp(A, false, Option.none()), caughtUp(B, true, Option.of(A))))
                .isLeft());

        assertEquals(mine, git(repo, "rev-parse", A.value()));
        assertTrue(!before.equals(mine));
        assertEquals("", git(repo, "branch", "--list", B.value()));
    }

    @Test
    void aBranchCheckedOutHereIsNotMovedUnderItsFiles() throws Exception {
        git(repo, "checkout", "-q", A.value());
        String here = git(repo, "rev-parse", A.value());
        assertTrue(remotes().fetchBranches(ORIGIN, List.of(A)).isRight());

        assertTrue(
                branches().catchUp(List.of(caughtUp(A, false, Option.none()))).isLeft());

        assertEquals(here, git(repo, "rev-parse", A.value()));
    }

    @Test
    void aBranchFollowingItsBranchOnTheRemoteUnderAnotherNameIsFastForwardedUnderItsOwn() throws Exception {
        BranchName mine = new BranchName("my-a");
        git(repo, "branch", "-q", "--track", mine.value(), "origin/" + A.value());
        assertTrue(remotes().fetchBranches(ORIGIN, List.of(A)).isRight());

        assertTrue(branches()
                .catchUp(List.of(new CaughtUpBranch(mine, new RemoteBranch(ORIGIN, A), false, Option.none())))
                .isRight());

        assertEquals(git(theirs, "rev-parse", A.value()), git(repo, "rev-parse", mine.value()));
    }
}
