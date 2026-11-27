package net.vanfleteren.jazygit.features.branches.branch.rebase;

import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.StatedBases;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Rebasing the checked out branch onto the highlighted one, or on its base branch (ADR 91).
 */
class RebaseUpdateTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName DEVELOP = new BranchName("develop");
    private static final BranchName PARENT = new BranchName("feat/a");
    private static final BranchName CHILD = new BranchName("feat/b");
    private static final BranchName OTHER = new BranchName("other");

    @Test
    void theCheckedOutRowAsksToRebaseOnTheParent() {
        Model model = withParents(checkedOut(CHILD), HashMap.of(CHILD, PARENT));

        assertEquals(
                Option.of(new RebasePopup(CHILD, new BranchRef.Local(CHILD), Option.of(new BranchRef.Local(PARENT)))),
                Update.update(model, new RebaseMsg.Requested(new BranchRef.Local(CHILD)))
                        .model()
                        .popup(RebasePopup.class));
    }

    @Test
    void anotherRowAsksToRebaseOntoItOrOnTheBase() {
        Model model = withParents(checkedOut(CHILD), HashMap.of(CHILD, PARENT));

        RebasePopup popup = Update.update(model, new RebaseMsg.Requested(new BranchRef.Local(OTHER)))
                .model()
                .popup(RebasePopup.class)
                .get();

        assertEquals(Option.of(new BranchRef.Local(OTHER)), popup.onto());
        assertEquals(Option.of(new BranchRef.Local(PARENT)), popup.base());
    }

    @Test
    void theBaseIsTheRemoteTrackingBranchWhenThereIsOne() {
        Model model = withRemote(withParents(checkedOut(CHILD), HashMap.of(CHILD, PARENT)), "feat/a", "main");

        assertEquals(
                Option.of(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, PARENT).tracking()),
                popup(model, OTHER).base());
        assertEquals(
                Option.of(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, MAIN).tracking()),
                popup(withRemote(checkedOut(CHILD), "main"), OTHER).base());
    }

    @Test
    void aRemoteBranchOnAnotherRemoteIsRebasedOntoToo() {
        RemoteBranch upstream = new RemoteBranch("upstream", MAIN);
        Model model = remoteBranchesRead(
                        checkedOut(CHILD), List.of(new RemoteRef(upstream, new Sha("2"), Instant.EPOCH)))
                .model();

        Next asked = Update.update(model, new RebaseMsg.Requested(upstream.tracking()));
        Next chosen = Update.update(asked.model(), new RebaseMsg.Chosen(RebaseMsg.Target.ONTO));

        assertEquals(List.of(new RebaseCmd(upstream.tracking())), chosen.cmds());
    }

    @Test
    void aRemoteBranchThatIsNotKnownCannotBeRebasedOnto() {
        Model model = withRemote(checkedOut(CHILD), "main");

        assertEquals(
                Option.none(),
                Update.update(
                                model,
                                new RebaseMsg.Requested(
                                        new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, OTHER).tracking()))
                        .model()
                        .popup(RebasePopup.class));
    }

    @Test
    void withoutAParentTheBaseIsTheMainBranch() {
        assertEquals(
                Option.of(new BranchRef.Local(MAIN)),
                popup(checkedOut(CHILD), CHILD).base());
    }

    @Test
    void aRootHasNoBase() {
        assertEquals(Option.none(), popup(checkedOut(MAIN), MAIN).base());

        // A branch a feature states is a root, as GitHub does of a protected one (ADR 169).
        Model develop = checkedOut(DEVELOP)
                .withPart(
                        Part.of("test.bases", StatedBases.NONE).stating(bases -> bases),
                        new StatedBases(HashMap.empty(), HashSet.of(DEVELOP), Option.none()));
        assertEquals(Option.none(), popup(develop, DEVELOP).base());
    }

    @Test
    void theCheckedOutRowCannotBeRebasedOntoItself() {
        assertEquals(Option.none(), popup(checkedOut(CHILD), CHILD).onto());
    }

    @Test
    void choosingTheBaseRebasesOnIt() {
        Model open = Update.update(
                        withParents(checkedOut(CHILD), HashMap.of(CHILD, PARENT)),
                        new RebaseMsg.Requested(new BranchRef.Local(OTHER)))
                .model();

        Next next = Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.BASE));

        assertEquals(List.of(new RebaseCmd(new BranchRef.Local(PARENT))), next.cmds());
        assertEquals(Option.none(), next.model().popup(RebasePopup.class));
    }

    @Test
    void choosingWhatDoesNotApplyDoesNothing() {
        Model open = Update.update(checkedOut(MAIN), new RebaseMsg.Requested(new BranchRef.Local(MAIN)))
                .model();

        assertEquals(
                List.of(),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.ONTO)).cmds());
        assertEquals(
                List.of(),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.BASE)).cmds());
    }

    @Test
    void aBaseThatIsBusyIsNotRebasedOn() {
        Model open = Update.update(checkedOut(CHILD), new RebaseMsg.Requested(new BranchRef.Local(CHILD)))
                .model();
        Model pushing = TestModels.busy(open, new BranchRef.Local(MAIN), PushCmd.PUSH);

        assertEquals(
                List.of(),
                Update.update(pushing, new RebaseMsg.Chosen(RebaseMsg.Target.BASE))
                        .cmds());
    }

    @Test
    void aBusyHighlightedBranchStillLetsTheBaseBeChosen() {
        Model model = withParents(checkedOut(CHILD), HashMap.of(CHILD, PARENT));
        Model pushing = TestModels.busy(model, new BranchRef.Local(OTHER), PushCmd.PUSH);

        Model open = Update.update(pushing, new RebaseMsg.Requested(new BranchRef.Local(OTHER)))
                .model();

        assertEquals(
                List.of(),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.ONTO)).cmds());
        assertEquals(
                List.of(new RebaseCmd(new BranchRef.Local(PARENT))),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.BASE)).cmds());
    }

    @Test
    void aBusyCheckedOutBranchOpensThePopupButIsNotRebased() {
        Model pushing = TestModels.busy(checkedOut(CHILD), new BranchRef.Local(CHILD), PushCmd.PUSH);

        Model open = Update.update(pushing, new RebaseMsg.Requested(new BranchRef.Local(OTHER)))
                .model();

        assertTrue(open.popup(RebasePopup.class).isDefined());
        assertEquals(
                List.of(),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.ONTO)).cmds());
        assertEquals(
                List.of(),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.BASE)).cmds());
    }

    private static Model withRemote(Model model, String... names) {
        return remoteBranchesRead(
                        model,
                        List.of(names)
                                .map(name -> new RemoteRef(
                                        new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName(name)),
                                        new Sha("2"),
                                        Instant.EPOCH)))
                .model();
    }

    private static RebasePopup popup(Model model, BranchName highlighted) {
        return Update.update(model, new RebaseMsg.Requested(new BranchRef.Local(highlighted)))
                .model()
                .popup(RebasePopup.class)
                .get();
    }

    /**
     * A model with the main branch, {@code develop}, {@code feat/a}, {@code feat/b} and {@code other}, of which
     * {@code current} is checked out.
     */
    private static Model checkedOut(BranchName current) {
        return localBranchesRead(
                        loaded(),
                        List.of(MAIN, DEVELOP, PARENT, CHILD, OTHER)
                                .map(name -> Fixtures.branch(
                                        name,
                                        name.equals(current),
                                        new Sha("1"),
                                        Instant.EPOCH,
                                        false,
                                        name.equals(MAIN))))
                .model();
    }

    private static Model withParents(Model model, io.vavr.collection.Map<BranchName, BranchName> parents) {
        return model.mapRepo(repo -> repo.withParents(parents));
    }
}
