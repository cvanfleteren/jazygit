package net.vanfleteren.jazygit.features.forgejo.builds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

public class BuildsUpdateTest {

    private static final Instant TIME = Instant.parse("2026-11-21T10:00:00Z");
    private static final RemoteName ORIGIN = new RemoteName("origin");

    static Sha sha(char digit) {
        return new Sha(String.valueOf(digit).repeat(40));
    }

    static RemoteRef remote(String name, Sha tip) {
        return new RemoteRef(new RemoteBranch(ORIGIN, new BranchName(name)), tip, TIME);
    }

    static Branch following(String name, String upstream) {
        return new Branch(
                new BranchName(name),
                false,
                sha('f'),
                TIME,
                Option.none(),
                true,
                false,
                new Tracking.Tracks(
                        new Upstream(new BranchRef.Remote(new RemoteBranch(ORIGIN, new BranchName(upstream))), 0, 0)),
                Option.none());
    }

    /**
     * A model with local branch {@code feature} following {@code origin/feature}, and {@code origin/main} too.
     */
    public static Model model() {
        Model model = TestModels.localBranchesRead(TestModels.loaded(), List.of(following("feature", "feature")))
                .model();
        return TestModels.remoteBranchesRead(model, List.of(remote("main", sha('1')), remote("feature", sha('2'))))
                .model();
    }

    private static List<Tip> reading(Next next) {
        return next.cmds()
                .filter(ReadBuilds.class::isInstance)
                .map(ReadBuilds.class::cast)
                .flatMap(ReadBuilds::tips);
    }

    @Test
    void theTipsTheLocalBranchesFollowComeFirstOnceEach() {
        assertEquals(List.of(new Tip(ORIGIN, sha('2')), new Tip(ORIGIN, sha('1'))), BuildsUpdate.tips(model()));
    }

    @Test
    void aTickReadsTheTipsNotReadAndMarksThemBeingRead() {
        Next next = BuildsUpdate.read(model());
        assertEquals(BuildsUpdate.tips(model()), reading(next));
        // While they are read, they are not asked for again.
        assertEquals(List.empty(), reading(BuildsUpdate.read(next.model())));
    }

    @Test
    void whatWasReadIsKeptAndOnlyRunningBuildsAreReadAgain() {
        Model model = model();
        List<Tip> tips = BuildsUpdate.tips(model);
        Model read = new BuildsMsg.Read(
                        tips, HashMap.of(sha('2'), CommitStatus.State.RUNNING, sha('1'), CommitStatus.State.PASSED))
                .apply(BuildsUpdate.read(model).model())
                .model();
        assertEquals(
                Option.of(CommitStatus.State.PASSED),
                read.part(ForgejoBuilds.PART).state(sha('1')));
        assertEquals(List.of(new Tip(ORIGIN, sha('2'))), reading(new BuildsMsg.Tick().apply(read)));
    }

    @Test
    void aTipThatCouldNotBeReadIsReadAgain() {
        Model model = model();
        Model read = new BuildsMsg.Read(BuildsUpdate.tips(model), HashMap.empty())
                .apply(BuildsUpdate.read(model).model())
                .model();
        assertEquals(BuildsUpdate.tips(model), reading(BuildsUpdate.read(read)));
    }

    @Test
    void afterAFetchWhatWasReadIsReadAgainAndStillShowsMeanwhile() {
        Model model = model();
        List<Tip> tips = BuildsUpdate.tips(model);
        Model read = new BuildsMsg.Read(
                        tips, HashMap.of(sha('2'), CommitStatus.State.FAILED, sha('1'), CommitStatus.State.PASSED))
                .apply(BuildsUpdate.read(model).model())
                .model();
        assertEquals(List.empty(), reading(BuildsUpdate.read(read)));

        Next fetched = ForgejoReactions.to(read, new KernelEvent.Fetched());
        assertEquals(tips, reading(fetched));
        assertEquals(
                Option.of(CommitStatus.State.FAILED),
                fetched.model().part(ForgejoBuilds.PART).state(sha('2')));
    }

    @Test
    void theBuildsAreReadOnceForgejoIsOnAndForAPush() {
        assertEquals(
                BuildsUpdate.tips(model()),
                reading(ForgejoReactions.to(model(), new KernelEvent.Switched(ForgejoFeature.ID, true))));
        assertEquals(List.empty(), reading(ForgejoReactions.to(model(), new KernelEvent.Switched("github", true))));
        assertEquals(
                BuildsUpdate.tips(model()), reading(ForgejoReactions.to(model(), new KernelEvent.OperationDone(true))));
        assertEquals(List.empty(), reading(ForgejoReactions.to(model(), new KernelEvent.OperationDone(false))));
    }

    @Test
    void atMostTwentyAreReadAtATime() {
        List<RemoteRef> many = List.range(0, 30).map(i -> remote("b" + i, new Sha(String.format("%040d", i))));
        Model model = TestModels.remoteBranchesRead(TestModels.loaded(), many).model();
        assertEquals(BuildsUpdate.AT_A_TIME, reading(BuildsUpdate.read(model)).size());
    }
}
