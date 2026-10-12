package net.vanfleteren.jazygit.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.state.Msg;
import org.junit.jupiter.api.Test;

class KeymapTest {

    private final Keymap keymap = Keymap.standard();

    @Test
    void aKeyActsOnTheSelection() {
        assertEquals(Option.of(new CheckoutMsg.Requested("x")),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.of("x")));
    }

    @Test
    void aKeyThatNeedsASelectionDoesNothingWithoutOne() {
        assertEquals(Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.none()));
    }

    @Test
    void unknownKeysDoNothing() {
        assertEquals(Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('Z'), Option.of("x")));
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
