package net.vanfleteren.jazygit.feature.help;


import io.vavr.control.Option;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpUpdateTest {

    @Test
    void requestOpensTheHelpOfThePanelAndCloseHidesIt() {
        Model open = Update.update(TestModels.loaded(), new HelpMsg.Requested(Scopes.BRANCHES.id())).model();
        assertEquals(Option.of(new HelpPopup(Scopes.BRANCHES.id())), open.popup());

        assertEquals(Option.none(), Update.update(open, new HelpMsg.Closed()).model().popup());
    }
}
