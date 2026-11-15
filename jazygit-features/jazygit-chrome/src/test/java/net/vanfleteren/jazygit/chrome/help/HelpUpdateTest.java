package net.vanfleteren.jazygit.chrome.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.chrome.theme.ThemeMsg;
import net.vanfleteren.jazygit.chrome.theme.ThemePopup;
import net.vanfleteren.jazygit.chrome.theme.ThemeScope;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

class HelpUpdateTest {

    @Test
    void requestOpensTheHelpOfThePanelAndCloseHidesIt() {
        Model open = Update.update(TestModels.loaded(), new HelpMsg.Requested(Scopes.BRANCHES.id(), List.empty()))
                .model();
        assertEquals(Option.of(new HelpPopup(Scopes.BRANCHES.id(), List.empty())), open.popup());

        assertEquals(
                Option.none(),
                Update.update(open, new PopupMsg.Dismissed(HelpPopup.class))
                        .model()
                        .popup());
    }

    @Test
    void choosingAKeyClosesTheHelpAndRunsIt() {
        Model open = Update.update(TestModels.loaded(), new HelpMsg.Requested(Scopes.BRANCHES.id(), List.empty()))
                .model();

        // The theme popup only opens when no other one is.
        Model chosen = Update.update(open, new HelpMsg.Chosen(new ThemeMsg.Requested(ThemeScope.EVERYWHERE)))
                .model();
        assertTrue(chosen.popup(ThemePopup.class).isDefined(), chosen.popup().toString());
    }
}
