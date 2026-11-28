package net.vanfleteren.jazygit.features.changes.commit;

import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.FEATURE;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.MAIN;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.ORIGIN_FEATURE;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.branch;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.withBranches;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.changes.commit.squash.SquashMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Whether a commit is read to be pushed before it is rewritten (#722): only on a branch following one on a remote,
 * and only while the warning is on.
 */
class PushedCheckTest {

    private static final Sha SHA = new Sha("c2");
    private static final SquashMsg.Requested THEN = new SquashMsg.Requested(SHA);

    @Test
    void onABranchFollowingARemoteBranchTheCommitIsReadAlsoAgainstTheMainBranch() {
        Model model = PushedModels.onFollowingBranch(TestModels.loaded());

        assertEquals(
                Option.of(new PushedCheckCmd(SHA, ORIGIN_FEATURE, Option.of(MAIN), THEN)),
                PushedCheck.of(true, model, SHA, THEN));
    }

    @Test
    void theWarningIsOnUnlessItIsSetOff() {
        Model model = PushedModels.onFollowingBranch(TestModels.loaded());

        assertEquals(
                PushedCheck.WARN,
                PushedCheck.first(model, SHA, THEN, () -> Next.of(model)).cmds().nonEmpty());
        assertEquals(Option.none(), PushedCheck.of(false, model, SHA, THEN));
    }

    @Test
    void onTheMainBranchItselfOnlyItsUpstreamCounts() {
        RemoteBranch originMain = new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, MAIN);
        Model model = withBranches(
                TestModels.loaded(),
                branch(MAIN, true, true, new Tracking.Tracks(new Upstream(new BranchRef.Remote(originMain), 0, 0))));

        assertEquals(
                Option.of(new PushedCheckCmd(SHA, originMain, Option.none(), THEN)),
                PushedCheck.of(true, model, SHA, THEN));
    }

    @Test
    void withoutAnUpstreamNothingIsRead() {
        Model model = withBranches(TestModels.loaded(), branch(FEATURE, true, false, new Tracking.None()));

        assertEquals(Option.none(), PushedCheck.of(true, model, SHA, THEN));
    }

    @Test
    void withAGoneUpstreamNothingIsRead() {
        Model model = withBranches(
                TestModels.loaded(),
                branch(FEATURE, true, false, new Tracking.Gone(new BranchRef.Remote(ORIGIN_FEATURE))));

        assertEquals(Option.none(), PushedCheck.of(true, model, SHA, THEN));
    }

    @Test
    void followingALocalBranchNothingIsRead() {
        Model model = withBranches(
                TestModels.loaded(),
                branch(
                        FEATURE,
                        true,
                        false,
                        new Tracking.Tracks(new Upstream(new BranchRef.Local(new BranchName("base")), 1, 0))));

        assertEquals(Option.none(), PushedCheck.of(true, model, SHA, THEN));
    }

    @Test
    void withoutABranchCheckedOutNothingIsRead() {
        Model model = withBranches(TestModels.loaded(), branch(FEATURE, false, false, new Tracking.None()));

        assertEquals(Option.none(), PushedCheck.of(true, model, SHA, THEN));
    }
}
