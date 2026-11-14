package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Predicate;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.ui.CommandLogPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import org.junit.jupiter.api.Test;

class FocusKeeperTest {

    private static final Predicate<String> POPUP = "confirm"::equals;

    @Test
    void anElementOfAPopupIsAside() {
        assertEquals(new Focus.Aside("confirm"), FocusKeeper.focusOf("confirm", POPUP));
    }

    @Test
    void theCommandLogIsAside() {
        assertEquals(CommandLogPanel.FOCUS, FocusKeeper.focusOf(CommandLogPanel.ID, POPUP));
    }

    @Test
    void anAreaOfTheContentPanelIsItsArea() {
        assertEquals(AreaId.COMMIT, FocusKeeper.focusOf(AreaId.COMMIT.value(), POPUP));
    }

    @Test
    void anythingElseIsAPane() {
        assertEquals(FilesPanel.PANE, FocusKeeper.focusOf(FilesPanel.ID, POPUP));
    }
}
