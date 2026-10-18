package net.vanfleteren.jazygit.keys;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.diff.DiffOptionsMsg;
import net.vanfleteren.jazygit.feature.github.browse.BrowseMsg;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.inprogress.InProgressMsg;
import net.vanfleteren.jazygit.feature.settings.SettingsPanelMsg;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Msg;
import org.junit.jupiter.api.Test;

class KeymapTest {

    private final Keymap keymap = Keymap.standard();

    @Test
    void ctrlSShowsTheSettings() {
        assertTrue(GlobalKeys.isSettingsKey(KeyEvent.ofChar('s', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        assertFalse(GlobalKeys.isSettingsKey(KeyEvent.ofChar('s')));
        assertEquals(
                Option.of(new SettingsPanelMsg.Requested()),
                keymap.handleGlobal(KeyEvent.ofChar('s', dev.tamboui.tui.event.KeyModifiers.CTRL)));
    }

    @Test
    void ctrlWTogglesWhetherChangesToWhitespaceOnlyAreShown() {
        assertEquals(
                Option.of(new DiffOptionsMsg.WhitespaceToggled()),
                keymap.handleGlobal(KeyEvent.ofChar('w', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('w')));
        assertTrue(keymap.globals().exists(global -> global.label().equals("ctrl+w")));
    }

    @Test
    void shiftMAsksWhatToDoWithTheOperationInProgressFromAnywhere() {
        assertEquals(Option.of(new InProgressMsg.Requested()), keymap.handleGlobal(KeyEvent.ofChar('M')));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('m')));
        assertTrue(keymap.globals().exists(global -> global.label().equals("M")));
    }

    @Test
    void aKeyCannotBeBoundTwiceInAPanel() {
        Keymap once = Keymap.empty()
                .add(Scopes.BRANCHES, Keymap.Binding.onSelected("x", "help.global.quit", CheckoutMsg.Requested::new));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> once.add(
                        Scopes.BRANCHES,
                        Keymap.Binding.onSelected("x", "help.global.quit", CheckoutMsg.Requested::new)));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> once.add(
                        Scopes.BRANCHES,
                        Keymap.Binding.onSelected(Keymap.HELP_KEY, "help.global.quit", CheckoutMsg.Requested::new)));
    }

    @Test
    void aKeyActsOnTheSelection() {
        assertEquals(
                Option.of(new CheckoutMsg.Requested(new Branch.Name("x"))),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.of(new Branch.Name("x"))));
    }

    @Test
    void shiftOOpensTheBranchOnGitHub() {
        assertEquals(
                Option.of(new BrowseMsg.Requested(new Branch.Name("x"))),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('O'), Option.of(new Branch.Name("x"))));
        RemoteBranch remote = new RemoteBranch("origin", new Branch.Name("x"));
        assertEquals(
                Option.of(new BrowseMsg.RemoteRequested(remote)),
                keymap.handle(Scopes.REMOTE_BRANCHES, KeyEvent.ofChar('O'), Option.of(remote)));
    }

    @Test
    void dDeletesARemoteBranch() {
        RemoteBranch remote = new RemoteBranch("origin", new Branch.Name("x"));
        assertEquals(
                Option.of(new DeleteBranchMsg.RemoteRequested(remote)),
                keymap.handle(Scopes.REMOTE_BRANCHES, KeyEvent.ofChar('d'), Option.of(remote)));
    }

    @Test
    void aKeyThatNeedsASelectionDoesNothingWithoutOne() {
        assertEquals(Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.none()));
    }

    @Test
    void aKeyWithCtrlOrAltIsNotTheKeyWithout() {
        Option<Branch.Name> selection = Option.of(new Branch.Name("x"));

        assertTrue(
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('r'), selection).isDefined());
        assertEquals(
                Option.none(),
                keymap.handle(
                        Scopes.BRANCHES, KeyEvent.ofChar('r', dev.tamboui.tui.event.KeyModifiers.CTRL), selection));
        assertEquals(
                Option.none(),
                keymap.handle(
                        Scopes.BRANCHES, KeyEvent.ofChar('r', dev.tamboui.tui.event.KeyModifiers.ALT), selection));
    }

    @Test
    void aPanelCanBindAKeyWithCtrl() {
        Keymap ctrlX = Keymap.empty()
                .add(
                        Scopes.BRANCHES,
                        Keymap.Binding.onSelected("ctrl+x", "help.global.quit", CheckoutMsg.Requested::new));
        Option<Branch.Name> selection = Option.of(new Branch.Name("x"));

        assertEquals(
                Option.of(new CheckoutMsg.Requested(new Branch.Name("x"))),
                ctrlX.handle(
                        Scopes.BRANCHES, KeyEvent.ofChar('x', dev.tamboui.tui.event.KeyModifiers.CTRL), selection));
        assertEquals(Option.none(), ctrlX.handle(Scopes.BRANCHES, KeyEvent.ofChar('x'), selection));
    }

    @Test
    void unknownKeysDoNothing() {
        assertEquals(
                Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('Z'), Option.of(new Branch.Name("x"))));
    }

    @Test
    void questionMarkAsksForTheHelpOfThePanel() {
        Option<Msg> msg = keymap.handle(Scopes.STASH, KeyEvent.ofChar('?'), Option.none());

        assertEquals(Option.of(new HelpMsg.Requested(Scopes.STASH.id(), List.of(Option.none(), Option.none()))), msg);
    }

    @Test
    void aPanelDeclaresEachKeyOnce() {
        for (Keymap.Scope<?> scope : List.of(Scopes.FILES, Scopes.BRANCHES, Scopes.COMMITS, Scopes.STASH)) {
            List<String> keys = keymap.bindings(scope.id()).map(Keymap.Binding::key);
            assertEquals(keys.size(), keys.distinct().size(), scope.id() + " " + keys);
            assertTrue(keymap.titleKey(scope.id()).isDefined());
        }
    }
}
