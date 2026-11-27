package net.vanfleteren.jazygit.features.forge.checks;

import static net.vanfleteren.jazygit.features.forge.checks.ForgeChecks.job;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.forge.checks.ForgeCheck.State;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The checks flow does what is the forge's own through the port the popup carries (ADR 209).
 */
class ChecksUpdateTest {

    private static final RemoteBranch FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final ForgeCheck FAILED = job("verify", 1, "Build", State.FAILED);

    private static Either<Failure, ChecksRead> read(List<ForgeCheck> checks) {
        return Either.right(new ChecksRead(new RepoSlug("owner/repo"), Option.none(), checks));
    }

    /**
     * The checks of {@code origin/feature} on {@code port}, read as {@code checks}.
     */
    private static Model shown(TestPort port, List<ForgeCheck> checks) {
        Model asked = Update.update(
                        TestModels.loaded().withNow(Instant.EPOCH),
                        new ChecksMsg.Requested(port, new BranchRef.Remote(FEATURE)))
                .model();
        return Update.update(asked, new ChecksMsg.Read(port, FEATURE, read(checks)))
                .model();
    }

    @Test
    void theChecksOfABranchAreReadByTheForgeAskedForThem() {
        Next next = Update.update(
                TestModels.loaded(), new ChecksMsg.Requested(TestPort.OTHER, new BranchRef.Remote(FEATURE)));

        assertEquals(
                Option.of(TestPort.OTHER), next.model().popup(ChecksPopup.class).map(ChecksPopup::port));
        assertEquals(List.of(new TestPort.Load(TestPort.OTHER, FEATURE)), next.cmds());
    }

    @Test
    void aBranchTheForgeHasNoChecksOfShowsNothing() {
        Next next = Update.update(
                TestModels.loaded(), new ChecksMsg.Requested(TestPort.ONE, new BranchRef.Local(new BranchName("x"))));

        assertEquals(Option.none(), next.model().popup(ChecksPopup.class));
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void onlyTheTicksOfTheForgeShownReadTheChecksAgain() {
        Model model = shown(TestPort.ONE, List.of(job("verify", 1, "Build", State.PENDING)));

        assertEquals(
                List.empty(),
                Update.update(model, new ChecksMsg.Tick(TestPort.OTHER)).cmds());
        assertEquals(
                List.of(new TestPort.Load(TestPort.ONE, FEATURE)),
                Update.update(model, new ChecksMsg.Tick(TestPort.ONE)).cmds());
    }

    @Test
    void theForgeDoesWhatElseItDoesWithTheChecksReadAlsoOnceTheyAreClosed() {
        Model closed = Update.update(shown(TestPort.ONE, List.empty()), new ChecksMsg.Closed())
                .model();

        Next next = Update.update(closed, new ChecksMsg.Read(TestPort.ONE, FEATURE, read(List.of(FAILED))));

        assertEquals(List.of(new TestPort.Asked(TestPort.ONE, "learn from 1 on origin/feature")), next.cmds());
    }

    @Test
    void theFailedJobsAreRunAgainByTheForgeWhichReadsAgainWhatThatChangedUnlessItRefusedThemAll() {
        Model asking = Update.update(shown(TestPort.ONE, List.of(FAILED)), new ChecksMsg.RerunRequested())
                .model();
        Next confirmed = Update.update(asking, new ChecksMsg.RerunConfirmed());
        RerunPlan.Run run = new RerunPlan.Run(1, "Build", "push", List.of(FAILED));
        TestPort.Asked cmd = new TestPort.Asked(TestPort.ONE, "rerun of 1");

        Next ran = Update.update(
                confirmed.model(),
                new ChecksMsg.Rerun(
                        TestPort.ONE, FEATURE, cmd, List.of(new RerunPlan.Outcome(run, Option.none())), List.empty()));
        Next refused = Update.update(
                confirmed.model(),
                new ChecksMsg.Rerun(
                        TestPort.ONE,
                        FEATURE,
                        cmd,
                        List.of(new RerunPlan.Outcome(run, Option.of(TestModels.refused("no")))),
                        List.empty()));

        assertEquals(List.of(cmd), confirmed.cmds());
        assertTrue(
                ran.cmds().contains(new TestPort.Asked(TestPort.ONE, "read the branches again")),
                ran.cmds().toString());
        assertTrue(
                !refused.cmds().contains(new TestPort.Asked(TestPort.ONE, "read the branches again")),
                refused.cmds().toString());
    }

    @Test
    void theLogOfTheCheckPickedIsReadByTheForge() {
        Next next = Update.update(shown(TestPort.ONE, List.of(FAILED)), new ChecksMsg.LogRequested());

        assertEquals(List.of(new TestPort.Asked(TestPort.ONE, "log of verify")), next.cmds());
    }
}
