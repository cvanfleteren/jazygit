package net.vanfleteren.jazygit.feature.branch.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

/**
 * Opening and closing a stack in the branches pane (ADR 72).
 */
class StackUpdateTest {

    @Test
    void aStackStartsOpenAndEnterOnItsHeaderClosesAndOpensIt() {
        Model loaded = TestModels.loaded();
        assertEquals(HashSet.empty(), loaded.repoView().closedStacks());

        Update.Next closed = Update.update(loaded, new StackMsg.Toggled(12));
        assertEquals(HashSet.of(12), closed.model().repoView().closedStacks());
        assertEquals(List.empty(), closed.cmds());

        assertEquals(
                HashSet.empty(),
                Update.update(closed.model(), new StackMsg.Toggled(12))
                        .model()
                        .repoView()
                        .closedStacks());
    }

    @Test
    void theKeysOfAHeaderToggleItsStackAndOpenItsMenu() {
        assertEquals(
                List.of(
                        Option.of(new StackMsg.Toggled(12)),
                        Option.of(new StackMsg.MenuRequested(Option.none(), Option.of(12)))),
                Keymap.standard().actions(Scopes.STACKS, Option.of(12)));
    }
}
