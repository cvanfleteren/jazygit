package net.vanfleteren.jazygit.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.state.Msg;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class KeymapTest {

    private final Keymap keymap = Keymap.standard();

    @Test
    void aKeyActsOnTheSelection() {
        assertEquals(Optional.of(new CheckoutMsg.Requested("x")),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Optional.of("x")));
    }

    @Test
    void aKeyThatNeedsASelectionDoesNothingWithoutOne() {
        assertEquals(Optional.empty(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Optional.empty()));
    }

    @Test
    void unknownKeysDoNothing() {
        assertEquals(Optional.empty(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('Z'), Optional.of("x")));
    }

    @Test
    void questionMarkAsksForTheHelpOfThePanel() {
        Optional<Msg> msg = keymap.handle(Scopes.STASH, KeyEvent.ofChar('?'), Optional.empty());

        assertEquals(Optional.of(new HelpMsg.Requested(Scopes.STASH.id())), msg);
    }

    @Test
    void aPanelDeclaresEachKeyOnce() {
        for (Keymap.Scope<?> scope : List.of(Scopes.FILES, Scopes.BRANCHES, Scopes.COMMITS, Scopes.STASH)) {
            List<String> keys = keymap.bindings(scope.id()).map(Keymap.Binding::key);
            assertEquals(keys.size(), keys.distinct().size(), scope.id() + " " + keys);
            assertTrue(keymap.titleKey(scope.id()).isPresent());
        }
    }
}
