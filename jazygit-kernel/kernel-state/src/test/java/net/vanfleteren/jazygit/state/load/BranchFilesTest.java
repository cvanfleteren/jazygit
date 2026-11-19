package net.vanfleteren.jazygit.state.load;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.localBranchesRead;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.BaseTip;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.view.Followed;
import net.vanfleteren.jazygit.state.view.Unlisted;
import org.junit.jupiter.api.Test;

/**
 * The files the files pane lists for a local branch highlighted in the branches (ADR 141).
 */
class BranchFilesTest {

    private static final Branch MAIN = Fixtures.branch(
            new BranchName("main"),
            false,
            new Sha("1111"),
            Instant.EPOCH,
            true,
            true,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))), 0, 0)));
    private static final Branch LOCAL = Fixtures.branch(new BranchName("local"), false, new Sha("4444"));

    @Test
    void aBranchListsItsFilesAgainstItsBase() {
        Model model = localBranchesRead(loaded(), List.of(LOCAL, MAIN)).model();

        assertEquals(
                Option.of(new Followed.Files(new Changes.OfBranch(
                        LOCAL.name(), new BaseTip(new BranchRef.Local(MAIN.name()), MAIN.tip()), LOCAL.tip()))),
                BranchFiles.of(model, LOCAL.name()));
    }

    @Test
    void theMainBranchListsWhatItsTipChanged() {
        Model model = localBranchesRead(loaded(), List.of(LOCAL, MAIN)).model();

        assertEquals(
                Option.of(new Followed.Files(new Changes.OfTip(MAIN.name(), MAIN.tip()))),
                BranchFiles.of(model, MAIN.name()));
    }

    @Test
    void theBranchCheckedOutListsTheFilesOfTheWorkingTree() {
        Branch checkedOut = Fixtures.branch(new BranchName("work"), true, new Sha("5555"));
        Model model = localBranchesRead(loaded(), List.of(checkedOut, MAIN)).model();

        assertEquals(Option.of(new Followed.WorkingTree()), BranchFiles.of(model, checkedOut.name()));
    }

    @Test
    void aBranchWithoutABaseHasNothingToList() {
        Model model = localBranchesRead(loaded(), List.of(LOCAL)).model();

        assertEquals(
                Option.of(new Followed.Nothing(new Unlisted.NoBase(LOCAL.name()))),
                BranchFiles.of(model, LOCAL.name()));
    }

    @Test
    void aBranchNotReadYetIsNotKnown() {
        assertEquals(Option.none(), BranchFiles.of(loaded(), new BranchName("elsewhere")));
    }
}
