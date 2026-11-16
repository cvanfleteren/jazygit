package net.vanfleteren.jazygit.branches.branch.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.view.Stacking;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Opening and closing a stack in the branches pane (ADR 72).
 */
class StackUpdateTest {

    @Test
    void aStackStartsOpenAndEnterOnItsHeaderClosesAndOpensIt() {
        Model loaded = TestModels.loaded();
        assertEquals(HashSet.empty(), loaded.repoView().branches().closedStacks());

        Update.Next closed = Update.update(loaded, new StackMsg.Toggled(12));
        assertEquals(HashSet.of(12), closed.model().repoView().branches().closedStacks());
        assertEquals(List.empty(), closed.cmds());

        assertEquals(
                HashSet.empty(),
                Update.update(closed.model(), new StackMsg.Toggled(12))
                        .model()
                        .repoView()
                        .branches()
                        .closedStacks());
    }

    @Test
    void theKeysOfAHeaderToggleItsStackAndOpenItsMenu() {
        assertEquals(
                List.of(
                        Option.of(new StackMsg.Toggled(12)),
                        Option.of(new StackMsg.MenuRequested(Option.none(), Option.of(12))),
                        Option.of(new StackMsg.ViewToggled())),
                Keymap.standard().actions(Scopes.STACKS, Option.of(12)));
    }

    @Test
    void theStacksShowAtFirstAndSHidesAndShowsThem() {
        Model loaded = TestModels.loaded();
        assertEquals(Stacking.STACKED, loaded.repoView().branches().stacking());

        Update.Next unstacked = Update.update(loaded, new StackMsg.ViewToggled());
        assertEquals(Stacking.UNSTACKED, unstacked.model().repoView().branches().stacking());
        assertEquals(List.empty(), unstacked.cmds());

        assertEquals(
                Stacking.STACKED,
                Update.update(unstacked.model(), new StackMsg.ViewToggled())
                        .model()
                        .repoView()
                        .branches()
                        .stacking());
    }

    @Test
    void aBranchHasTheKeyThatHidesTheStacksToo() {
        assertEquals(
                true,
                Keymap.standard()
                        .actions(Scopes.BRANCHES, Option.none())
                        .contains(Option.of(new StackMsg.ViewToggled())));
    }
}
