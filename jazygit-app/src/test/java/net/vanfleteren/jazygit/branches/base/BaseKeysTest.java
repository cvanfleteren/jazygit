package net.vanfleteren.jazygit.branches.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.branches.branch.base.BaseMsg;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * {@code b} on a branch asks to set its base (ADR 143), and the commits no longer take it (ADR 142).
 */
class BaseKeysTest {

    private static final BranchName FEATURE = new BranchName("feature");

    @Test
    void bOnABranchAsksToSetItsBase() {
        Keymap keys = Keymap.standard();

        assertEquals(
                Option.of(new BaseMsg.Requested(FEATURE)),
                keys.handle(Scopes.BRANCHES, KeyEvent.ofChar('b'), Option.of(FEATURE)));
        assertEquals(Option.none(), keys.handle(Scopes.COMMITS, KeyEvent.ofChar('b'), Option.none()));
        assertFalse(Messages.get("branches.help.branches.base").startsWith("!"));
    }
}
