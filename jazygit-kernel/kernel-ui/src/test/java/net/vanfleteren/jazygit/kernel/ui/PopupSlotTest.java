package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import org.junit.jupiter.api.Test;

/**
 * Where the focus goes back to outside the popups.
 */
class PopupSlotTest {

    @Test
    void aScreenNotFocusedYetStartsWithItsFirstPane() {
        // A screen the kernel does not know (ADR 168).
        Screen other = new Screen(
                "other",
                List.of(new PaneId("top"), new PaneId("bottom")),
                Settings.leftWidth("ui.other-width"),
                Settings.leftHeights("ui.other-heights", List.of(50)));

        assertEquals(PaneId.FILES, PopupSlot.focusedPane(TestModels.loaded()));
        assertEquals(
                new PaneId("top"), PopupSlot.focusedPane(TestModels.loaded().withScreen(other)));
    }
}
