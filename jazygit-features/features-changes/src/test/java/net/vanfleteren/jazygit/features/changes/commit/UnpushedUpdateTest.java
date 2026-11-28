package net.vanfleteren.jazygit.features.changes.commit;

import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.FEATURE;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.MAIN;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.ORIGIN_FEATURE;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.branch;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.withBranches;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Keeping which commits of the log of HEAD are not pushed yet, for the commits pane to mark (#722).
 */
class UnpushedUpdateTest {

    private static final Sha HEAD = new Sha("c3");
    private static final Sha UPSTREAM = new Sha("c2");
    private static final Sha MAIN_TIP = new Sha("c1");
    private static final Unpushed.Tips TIPS = new Unpushed.Tips(HEAD, List.of(UPSTREAM, MAIN_TIP));

    private static Model following() {
        Commit head = Fixtures.commit(HEAD, "Ada", "ada@example.com", Instant.EPOCH, "Head", "", List.of(UPSTREAM));
        Model model = TestModels.loaded()
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.of(head)))
                        .withRemoteBranches(new Loadable.Loaded<>(List.of(
                                new RemoteRef(ORIGIN_FEATURE, UPSTREAM, Instant.EPOCH),
                                new RemoteRef(
                                        new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, MAIN),
                                        MAIN_TIP,
                                        Instant.EPOCH)))));
        return PushedModels.onFollowingBranch(model);
    }

    @Test
    void theCommitsAreReadAgainstTheUpstreamAndTheMainBranchOnItsRemote() {
        Next next = UnpushedUpdate.reacting(following(), new KernelEvent.LogRead());

        assertEquals(List.of(new ReadUnpushed(TIPS)), next.cmds());
        assertEquals(Option.of(TIPS), next.model().part(Unpushed.PART).asked());
    }

    @Test
    void whatWasAskedForAlreadyIsNotReadAgain() {
        Model asked =
                UnpushedUpdate.reacting(following(), new KernelEvent.LogRead()).model();

        assertEquals(List.of(), Update.update(asked, new UnpushedMsg.Tick()).cmds());
    }

    @Test
    void whatWasReadIsShownWhileTheTipsStayWhereTheyWere() {
        Model asked =
                UnpushedUpdate.reacting(following(), new KernelEvent.LogRead()).model();

        Model read = Update.update(asked, new UnpushedMsg.Read(TIPS, HashSet.of(HEAD)))
                .model();

        assertEquals(HashSet.of(HEAD), UnpushedUpdate.shown(read));
    }

    @Test
    void onceTheUpstreamMovedWhatWasReadIsNotShownAndItIsReadAgain() {
        Model asked =
                UnpushedUpdate.reacting(following(), new KernelEvent.LogRead()).model();
        Model read = Update.update(asked, new UnpushedMsg.Read(TIPS, HashSet.of(HEAD)))
                .model();
        Model pushed = read.mapRepo(repo -> repo.withRemoteBranches(new Loadable.Loaded<>(List.of(
                new RemoteRef(ORIGIN_FEATURE, HEAD, Instant.EPOCH),
                new RemoteRef(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, MAIN), MAIN_TIP, Instant.EPOCH)))));

        Next next = Update.update(pushed, new UnpushedMsg.Tick());

        assertEquals(HashSet.empty(), UnpushedUpdate.shown(pushed));
        assertEquals(List.of(new ReadUnpushed(new Unpushed.Tips(HEAD, List.of(HEAD, MAIN_TIP)))), next.cmds());
    }

    @Test
    void whatWasReadForTipsNoLongerAskedAboutIsDropped() {
        Model asked =
                UnpushedUpdate.reacting(following(), new KernelEvent.LogRead()).model();
        Unpushed.Tips before = new Unpushed.Tips(UPSTREAM, List.of(UPSTREAM));

        Model read = Update.update(asked, new UnpushedMsg.Read(before, HashSet.of(UPSTREAM)))
                .model();

        assertEquals(Option.none(), read.part(Unpushed.PART).read());
    }

    @Test
    void withoutAnUpstreamNothingIsReadNorShown() {
        Model model = withBranches(following(), branch(FEATURE, true, false, new Tracking.None()));

        Next next = UnpushedUpdate.reacting(model, new KernelEvent.LogRead());

        assertEquals(List.of(), next.cmds());
        assertEquals(HashSet.empty(), UnpushedUpdate.shown(next.model()));
    }

    @Test
    void onTheMainBranchItselfOnlyItsUpstreamCounts() {
        RemoteBranch originMain = new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, MAIN);
        Model model = withBranches(
                following(),
                branch(MAIN, true, true, new Tracking.Tracks(new Upstream(new BranchRef.Remote(originMain), 1, 0))));

        assertEquals(
                List.of(new ReadUnpushed(new Unpushed.Tips(HEAD, List.of(MAIN_TIP)))),
                UnpushedUpdate.reacting(model, new KernelEvent.LogRead()).cmds());
    }

    @Test
    void theCommitsAreReadWithOneRevList() {
        Msg read = new ReadUnpushed(TIPS).run(new Env(StubGit.of().withHistory(new StubHistory() {
            @Override
            public io.vavr.control.Either<GitError, io.vavr.collection.Set<Sha>> onlyOn(
                    Sha tip, List<Sha> others, int max) {
                return io.vavr.control.Either.right(
                        tip.equals(HEAD) && others.equals(TIPS.shared()) ? HashSet.of(HEAD) : HashSet.empty());
            }
        })));

        assertEquals(new UnpushedMsg.Read(TIPS, HashSet.of(HEAD)), read);
    }
}
