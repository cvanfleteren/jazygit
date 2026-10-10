package net.vanfleteren.jazygit.feature.help;


import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpUpdateTest {

    @Test
    void requestOpensTheHelpOfThePanelAndCloseHidesIt() {
        Model open = Update.update(TestModels.loaded(), new HelpMsg.Requested(HelpTopic.BRANCHES)).model();
        assertEquals(Optional.of(new HelpPopup(HelpTopic.BRANCHES)), open.popup());

        assertEquals(Optional.empty(), Update.update(open, new HelpMsg.Closed()).model().popup());
    }
}
