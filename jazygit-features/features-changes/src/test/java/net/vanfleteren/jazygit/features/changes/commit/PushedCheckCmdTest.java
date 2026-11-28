package net.vanfleteren.jazygit.features.changes.commit;

import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.MAIN;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.ORIGIN_FEATURE;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.warning;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubBranches;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.squash.SquashMsg;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.junit.jupiter.api.Test;

/**
 * Reading whether a commit is on the upstream, and on the main branch, before it is rewritten (#722).
 */
class PushedCheckCmdTest {

    private static final Sha SHA = new Sha("c2");
    private static final BranchRef UPSTREAM = new BranchRef.Remote(ORIGIN_FEATURE);
    private static final BranchRef ORIGIN_MAIN =
            new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, MAIN));
    private static final BranchRef LOCAL_MAIN = new BranchRef.Local(MAIN);
    private static final PushedCheckCmd CHECK =
            new PushedCheckCmd(SHA, ORIGIN_FEATURE, Option.of(MAIN), new SquashMsg.Requested(SHA));

    @Test
    void theUpstreamAndTheMainBranchOnItsRemoteAndHereAreReadInOneGo() {
        java.util.List<List<BranchRef>> asked = new java.util.ArrayList<>();

        CHECK.run(new Env(StubGit.of().withBranches(new StubBranches() {
            @Override
            public Either<GitError, List<BranchRef>> containing(Sha commit, List<BranchRef> among) {
                asked.add(among);
                return Either.right(List.empty());
            }
        })));

        assertEquals(java.util.List.of(List.of(UPSTREAM, ORIGIN_MAIN, LOCAL_MAIN)), asked);
    }

    @Test
    void aCommitOnlyHereNeedsNoWarning() {
        assertEquals(new SquashMsg.Checked(SHA, Option.none()), run(List.empty()));
    }

    @Test
    void aCommitOnTheUpstreamWarnsOfTheForcePush() {
        assertEquals(new SquashMsg.Checked(SHA, Option.of(warning(false))), run(List.of(UPSTREAM)));
    }

    @Test
    void aCommitOnTheMainBranchTooWarnsMoreStrongly() {
        assertEquals(new SquashMsg.Checked(SHA, Option.of(warning(true))), run(List.of(UPSTREAM, ORIGIN_MAIN)));
        assertEquals(new SquashMsg.Checked(SHA, Option.of(warning(true))), run(List.of(UPSTREAM, LOCAL_MAIN)));
    }

    @Test
    void aCommitOnlyOnTheMainBranchIsNotPushedFromHere() {
        assertEquals(new SquashMsg.Checked(SHA, Option.none()), run(List.of(LOCAL_MAIN)));
    }

    @Test
    void whenGitCannotTellTheRewriteGoesOnWithoutAWarning() {
        Msg done = CHECK.run(new Env(StubGit.of().withBranches(new StubBranches() {
            @Override
            public Either<GitError, List<BranchRef>> containing(Sha commit, List<BranchRef> among) {
                return Either.left(new GitError.UnexpectedOutput("garbage"));
            }
        })));

        assertEquals(new SquashMsg.Checked(SHA, Option.none()), done);
    }

    private static Msg run(List<BranchRef> having) {
        return CHECK.run(new Env(StubGit.of().withBranches(new StubBranches() {
            @Override
            public Either<GitError, List<BranchRef>> containing(Sha commit, List<BranchRef> among) {
                return Either.right(having.filter(among::contains));
            }
        })));
    }
}
