package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * Which commits of HEAD and of the highlighted branch are not on their base: what the base is, and when that is read.
 */
class AheadUpdateTest {

    private static final Branch.Name MAIN = new Branch.Name("main");
    private static final Branch.Name FEATURE = new Branch.Name("feature");
    private static final BranchRef FEATURE_LOG = new BranchRef.Local(FEATURE);

    private static BranchRef origin(String name) {
        return new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new Branch.Name(name)));
    }

    private static Ahead ahead(BranchRef base) {
        return new Ahead(base, FEATURE_COMMITS, false, Option.none());
    }

    @Test
    void theBaseIsTheParentElseTheMainBranchTheRemoteOneFirst() {
        Cmd.LoadAhead load = new Cmd.LoadAhead(Option.of(FEATURE_LOG), Option.of(FEATURE), Option.of(MAIN));

        assertEquals(
                List.of(
                        origin("parent"),
                        new BranchRef.Local(new Branch.Name("parent")),
                        origin("main"),
                        new BranchRef.Local(MAIN)),
                load.bases(Option.of(new Branch.Name("parent"))));
        assertEquals(List.of(origin("main"), new BranchRef.Local(MAIN)), load.bases(Option.none()));
    }

    @Test
    void aBranchIsNoBaseOfItself() {
        assertEquals(
                List.empty(),
                new Cmd.LoadAhead(Option.of(origin("main")), Option.of(MAIN), Option.of(MAIN)).bases(Option.none()));
    }

    @Test
    void aDetachedHeadIsComparedWithTheMainBranch() {
        assertEquals(
                List.of(origin("main"), new BranchRef.Local(MAIN)),
                new Cmd.LoadAhead(Option.none(), Option.none(), Option.of(MAIN)).bases(Option.none()));
    }

    @Test
    void theCommitsOfHeadNotOnItsBaseAreReadOnceItsLogIs() {
        Update.Next next = Update.update(loaded(), new LoadMsg.CommitsLoaded(COMMITS));

        assertTrue(next.cmds().contains(AheadUpdate.ofHead(next.model())));
        Model read = Update.update(
                        next.model(),
                        new LoadMsg.AheadLoaded(AheadUpdate.ofHead(next.model()), Option.of(ahead(origin("main")))))
                .model();
        assertEquals(Option.of(ahead(origin("main"))), AheadUpdate.head(read));
        assertEquals(FEATURE_COMMITS.headOption(), read.commit(new Sha("ffff")));
    }

    @Test
    void whatIsReadForABranchNoLongerHighlightedIsDropped() {
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(FEATURE_LOG))
                .model();
        Update.Next logRead = Update.update(selected, new LoadMsg.BranchLogLoaded(FEATURE_LOG, FEATURE_COMMITS));
        Cmd.LoadAhead load = AheadUpdate.of(logRead.model(), FEATURE_LOG);
        assertEquals(List.of(load), logRead.cmds());

        // The log of the other branch is read before what is not on its base, which drops the one of before.
        BranchRef main = new BranchRef.Local(MAIN);
        Model other = Update.update(logRead.model(), new SelectionMsg.BranchSelected(main))
                .model();
        Model otherRead =
                Update.update(other, new LoadMsg.BranchLogLoaded(main, COMMITS)).model();
        Model late = Update.update(otherRead, new LoadMsg.AheadLoaded(load, Option.of(ahead(origin("main")))))
                .model();
        assertEquals(Option.none(), AheadUpdate.branch(late, FEATURE_LOG));
        assertEquals(otherRead.repoView(), late.repoView());

        Model read = Update.update(logRead.model(), new LoadMsg.AheadLoaded(load, Option.of(ahead(origin("main")))))
                .model();
        assertEquals(Option.of(ahead(origin("main"))), AheadUpdate.branch(read, FEATURE_LOG));
    }

    @Test
    void theCommitsAreReadAgainWhenTheBaseMoved() {
        Model model = loaded();
        Cmd.LoadAhead load = AheadUpdate.ofHead(model);
        Model read = Update.update(model, new LoadMsg.AheadLoaded(load, Option.of(ahead(new BranchRef.Local(FEATURE)))))
                .model();

        Update.Next moved = localBranchesRead(
                read, List.of(new Branch(MAIN, true, new Sha("aaaa")), new Branch(FEATURE, false, new Sha("eeee"))));
        Update.Next still = localBranchesRead(read, BRANCHES);

        assertTrue(moved.cmds().contains(load));
        assertFalse(still.cmds().contains(load));
    }

    @Test
    void aFailedReadIsTriedAgainOnTheNextRefresh() {
        Model model = loaded();
        Cmd.LoadAhead load = AheadUpdate.ofHead(model);

        Model failed = Update.update(model, load.failed(refused("no"))).model();

        assertEquals(Option.none(), AheadUpdate.head(failed));
        assertTrue(Update.refresh(failed).cmds().contains(load));
    }

    @Test
    void togglingSwitchesBetweenTheWholeLogAndOnlyWhatIsNotOnTheBase() {
        Model only = Update.update(loaded(), new AheadMsg.Toggled()).model();

        assertTrue(only.repoView().onlyAhead());
        assertFalse(
                Update.update(only, new AheadMsg.Toggled()).model().repoView().onlyAhead());
    }
}
