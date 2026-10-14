package net.vanfleteren.jazygit.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Msg;
import org.junit.jupiter.api.Test;

class KeymapTest {

    private final Keymap keymap = Keymap.standard();

    @Test
    void ctrlTAsksForTheTheme() {
        assertTrue(GlobalKeys.isThemeKey(KeyEvent.ofChar('t', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        assertFalse(GlobalKeys.isThemeKey(KeyEvent.ofChar('t')));
        assertFalse(GlobalKeys.isThemeKey(KeyEvent.ofChar('x', dev.tamboui.tui.event.KeyModifiers.CTRL)));
    }

    @Test
    void aKeyCannotBeBoundTwiceInAPanel() {
        Keymap once = Keymap.empty().add(Scopes.BRANCHES, Keymap.Binding.<Branch.Name>onSelected("x", "help.global.quit", CheckoutMsg.Requested::new));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> once.add(Scopes.BRANCHES,
                Keymap.Binding.<Branch.Name>onSelected("x", "help.global.quit", CheckoutMsg.Requested::new)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> once.add(Scopes.BRANCHES,
                Keymap.Binding.<Branch.Name>onSelected(Keymap.HELP_KEY, "help.global.quit", CheckoutMsg.Requested::new)));
    }

    @Test
    void aKeyActsOnTheSelection() {
        assertEquals(Option.of(new CheckoutMsg.Requested(new Branch.Name("x"))),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.of(new Branch.Name("x"))));
    }

    @Test
    void aKeyThatNeedsASelectionDoesNothingWithoutOne() {
        assertEquals(Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.none()));
    }

    @Test
    void unknownKeysDoNothing() {
        assertEquals(Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('Z'), Option.of(new Branch.Name("x"))));
    }

    @Test
    void questionMarkAsksForTheHelpOfThePanel() {
        Option<Msg> msg = keymap.handle(Scopes.STASH, KeyEvent.ofChar('?'), Option.none());

        assertEquals(Option.of(new HelpMsg.Requested(Scopes.STASH.id())), msg);
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
