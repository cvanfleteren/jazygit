package net.vanfleteren.jazygit.features.branches.branch;

import static net.vanfleteren.jazygit.kernel.state.TestModels.branchesLoaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.push.SetUpstreamPopup;
import net.vanfleteren.jazygit.features.branches.branch.rebase.RebaseMsg;
import net.vanfleteren.jazygit.features.branches.branch.rebase.RebasePopup;
import net.vanfleteren.jazygit.features.branches.branch.upstream.UpstreamUpdate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import org.junit.jupiter.api.Test;

/**
 * What goes by the base remote of the repository, which is not always {@code origin} (ADR 215): in a fork, the base is
 * the project the fork was made of.
 */
class BaseRemoteUpdateTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteName UPSTREAM = new RemoteName("upstream");
    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", MAIN);
    private static final RemoteBranch UPSTREAM_MAIN = new RemoteBranch(UPSTREAM, MAIN);

    /**
     * A fork: {@code origin/main} is behind, {@code upstream/main} is where {@code main} is; the branches go by
     * {@code upstream}. The local {@code main} follows {@code upstream/main} and is not ahead; {@code feature} is
     * checked out.
     */
    private static Model fork() {
        Branch main = Fixtures.branch(
                MAIN,
                false,
                new Sha("bbbb"),
                Instant.EPOCH,
                true,
                true,
                Option.of(new Upstream(new BranchRef.Remote(UPSTREAM_MAIN), 0, 0)));
        Branch feature = Fixtures.branch(FEATURE, true, new Sha("ffff"), Instant.EPOCH, false, false, Option.none());
        return Update.update(
                        loaded(),
                        branchesLoaded(new AllBranches(
                                List.of(feature, main),
                                List.of(
                                        new RemoteRef(ORIGIN_MAIN, new Sha("aaaa"), Instant.EPOCH),
                                        new RemoteRef(UPSTREAM_MAIN, new Sha("bbbb"), Instant.EPOCH)),
                                RemoteSetup.of(UPSTREAM))))
                .model();
    }

    @Test
    void theModelKnowsTheBaseRemote() {
        assertEquals(Option.of(UPSTREAM), fork().repo().baseRemote());
    }

    @Test
    void inAForkTheDivergenceFromTheBaseIsFromTheBaseRemoteNotFromOrigin() {
        assertEquals(Option.of(new BranchRef.Remote(UPSTREAM_MAIN)), UpstreamUpdate.base(fork()));
    }

    @Test
    void inAForkRebasingOnTheBaseTakesTheBranchOfTheBaseRemote() {
        RebasePopup popup = Update.update(fork(), new RebaseMsg.Requested(new BranchRef.Local(MAIN)))
                .model()
                .popup(RebasePopup.class)
                .get();

        assertEquals(Option.of(new BranchRef.Remote(UPSTREAM_MAIN)), popup.base());
    }

    @Test
    void aBranchWithoutAnUpstreamIsAskedToPushToTheRemoteItGoesTo() {
        Branch feature = new Branch(
                FEATURE,
                true,
                new Sha("ffff"),
                Instant.EPOCH,
                Option.none(),
                false,
                false,
                new Tracking.None(),
                Option.none(),
                Option.of(UPSTREAM));

        assertEquals(new SetUpstreamPopup(FEATURE, "upstream feature", Option.none()), SetUpstreamPopup.of(feature));
    }
}
