package net.vanfleteren.jazygit.kernel.state.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import org.junit.jupiter.api.Test;

/**
 * What a section of the settings panel keeps while the panel is open (ADR 163).
 */
class SettingsPanelPopupTest {

    private static final SectionState<Option<String>> READ = new SectionState<>("test:read", Option.none());

    @Test
    void aSectionThatKeptNothingHasItsInitialState() {
        assertEquals(Option.none(), new SettingsPanelPopup(Settings.DEFAULT).section(READ));
    }

    @Test
    void aSectionKeepsWhatItIsGiven() {
        SettingsPanelPopup panel = new SettingsPanelPopup(Settings.DEFAULT).with(READ, Option.some("read"));

        assertEquals(Option.some("read"), panel.section(READ));
    }

    @Test
    void aSectionSetBackToItsInitialStateKeepsNothing() {
        SettingsPanelPopup fresh = new SettingsPanelPopup(Settings.DEFAULT);

        SettingsPanelPopup back = fresh.with(READ, Option.some("read")).with(READ, Option.none());

        assertTrue(back.sections().isEmpty());
        assertEquals(fresh, back);
    }
}
