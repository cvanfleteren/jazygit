package net.vanfleteren.jazygit.features.github.stack;

import static net.vanfleteren.jazygit.app.GitHubKeys.github;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Opening and closing a stack in the branches pane (ADR 72), a group of the pane (ADR 167).
 */
class StackUpdateTest {

    @Test
    void aStackStartsOpenAndEnterOnItsHeaderClosesAndOpensIt() {
        Model loaded = TestModels.loaded();
        assertEquals(HashSet.empty(), loaded.repoView().branches().closedGroups());

        Update.Next closed = Update.update(loaded, new StackMsg.Toggled(new StackId(12)));
        assertEquals(
                HashSet.of("stack:12"), closed.model().repoView().branches().closedGroups());
        assertEquals(List.empty(), closed.cmds());

        assertEquals(
                HashSet.empty(),
                Update.update(closed.model(), new StackMsg.Toggled(new StackId(12)))
                        .model()
                        .repoView()
                        .branches()
                        .closedGroups());
    }

    @Test
    void theKeysOfAHeaderToggleItsStackAndOpenItsMenu() {
        assertEquals(
                List.of(
                        Option.of(github(new StackMsg.Toggled(new StackId(12)))),
                        Option.of(github(new StackMsg.MenuRequested(Option.none(), Option.of(new StackId(12))))),
                        Option.of(github(new StackMsg.ViewToggled()))),
                Keymap.standard().actions(StackAction.STACKS, Option.of(new StackId(12))));
    }

    @Test
    void theStacksShowAtFirstAndSHidesAndShowsThem() {
        Model loaded = TestModels.loaded();
        assertEquals(true, loaded.repoView().branches().grouped());

        Update.Next ungrouped = Update.update(loaded, new StackMsg.ViewToggled());
        assertEquals(false, ungrouped.model().repoView().branches().grouped());
        assertEquals(List.empty(), ungrouped.cmds());

        assertEquals(
                true,
                Update.update(ungrouped.model(), new StackMsg.ViewToggled())
                        .model()
                        .repoView()
                        .branches()
                        .grouped());
    }

    @Test
    void aBranchHasTheKeyThatHidesTheStacksToo() {
        assertEquals(
                true,
                Keymap.standard()
                        .actions(Scopes.BRANCHES, Option.none())
                        .contains(Option.of(github(new StackMsg.ViewToggled()))));
    }
}
