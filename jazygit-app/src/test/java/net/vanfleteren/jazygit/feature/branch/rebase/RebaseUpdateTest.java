package net.vanfleteren.jazygit.feature.branch.rebase;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
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
                Option.of(new RebasePopup(CHILD, CHILD, Option.of(PARENT))),
                Update.update(model, new RebaseMsg.Requested(CHILD)).model().popup(RebasePopup.class));
    }

    @Test
    void anotherRowAsksToRebaseOntoItOrOnTheBase() {
        Model model = withParents(checkedOut(CHILD), HashMap.of(CHILD, PARENT));

        RebasePopup popup = Update.update(model, new RebaseMsg.Requested(OTHER))
                .model()
                .popup(RebasePopup.class)
                .get();

        assertEquals(Option.of(OTHER), popup.onto());
        assertEquals(Option.of(PARENT), popup.base());
    }

    @Test
    void withoutAParentTheBaseIsTheMainBranch() {
        assertEquals(Option.of(MAIN), popup(checkedOut(CHILD), CHILD).base());
    }

    @Test
    void aRootHasNoBase() {
        assertEquals(Option.none(), popup(checkedOut(MAIN), MAIN).base());

        Model develop =
                checkedOut(DEVELOP).mapGitHub(gitHub -> gitHub.withProtectedBranches(Option.of(HashSet.of(DEVELOP))));
        assertEquals(Option.none(), popup(develop, DEVELOP).base());
    }

    @Test
    void theCheckedOutRowCannotBeRebasedOntoItself() {
        assertEquals(Option.none(), popup(checkedOut(CHILD), CHILD).onto());
    }

    @Test
    void choosingTheBaseRebasesOnIt() {
        Model open = Update.update(
                        withParents(checkedOut(CHILD), HashMap.of(CHILD, PARENT)), new RebaseMsg.Requested(OTHER))
                .model();

        Next next = Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.BASE));

        assertEquals(List.of(new RebaseCmd(PARENT)), next.cmds());
        assertEquals(Option.none(), next.model().popup(RebasePopup.class));
    }

    @Test
    void choosingWhatDoesNotApplyDoesNothing() {
        Model open =
                Update.update(checkedOut(MAIN), new RebaseMsg.Requested(MAIN)).model();

        assertEquals(
                List.of(),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.ONTO)).cmds());
        assertEquals(
                List.of(),
                Update.update(open, new RebaseMsg.Chosen(RebaseMsg.Target.BASE)).cmds());
    }

    @Test
    void aBaseThatIsBusyIsNotRebasedOn() {
        Model open =
                Update.update(checkedOut(CHILD), new RebaseMsg.Requested(CHILD)).model();
        Model pushing = TestModels.busy(open, new BranchRef.Local(MAIN), PushCmd.PUSH);

        assertEquals(
                List.of(),
                Update.update(pushing, new RebaseMsg.Chosen(RebaseMsg.Target.BASE))
                        .cmds());
    }

    private static RebasePopup popup(Model model, BranchName highlighted) {
        return Update.update(model, new RebaseMsg.Requested(highlighted))
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
