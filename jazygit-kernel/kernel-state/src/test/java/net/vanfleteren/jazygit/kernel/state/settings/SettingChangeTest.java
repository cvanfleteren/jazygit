package net.vanfleteren.jazygit.kernel.state.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.kernel.state.preferences.ShowAgain;
import org.junit.jupiter.api.Test;

/**
 * The one way a setting changes (ADR 163), which every section of the settings panel relies on.
 */
class SettingChangeTest {

    private static final SettingKey<Boolean> ON = SettingKey.of("test.on", false, Codec.bool());
    private static final SettingKey<Boolean> REOPENS =
            ON.withPath("test.reopens").withReopens(true);

    @Test
    void aSettingThatHasTheValueAlreadyChangesNothing() {
        Model model = TestModels.loaded();

        Next next = SettingChange.set(model, ON, false);

        assertEquals(model, next.model());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void aChangedSettingIsKeptAndSaved() {
        Model model = TestModels.loaded();
        Settings kept = model.preferences().settings();

        Next next = SettingChange.set(model, ON, true);

        assertEquals(kept.with(ON, true), next.model().preferences().settings());
        assertEquals(List.<Cmd>of(new SaveSettings(kept, kept.with(ON, true))), next.cmds());
    }

    @Test
    void aSettingThatReopensShowsTheRepositoryAgainWhereItWas() {
        Model model = TestModels.loaded();
        Settings kept = model.preferences().settings();

        Next next = SettingChange.set(model, REOPENS, true);

        assertEquals(
                List.<Cmd>of(
                        new SaveSettings(kept, kept.with(REOPENS, true)), new ShowAgain(Landing.of(model.repoView()))),
                next.cmds());
    }

    @Test
    void anOpenSettingsPanelShowsTheChangedSetting() {
        Model model = TestModels.loaded();
        SettingsPanelPopup panel = new SettingsPanelPopup(model.preferences().settings());

        Next next = SettingChange.set(model.openPopup(panel), ON, true);

        assertEquals(
                Option.of(panel.withSettings(model.preferences().settings().with(ON, true))),
                next.model().popup());
    }

    @Test
    void withoutTheSettingsPanelOpenThePopupStaysAsItWas() {
        Model model = TestModels.loaded();

        Next next = SettingChange.set(model, ON, true);

        assertEquals(model.popup(), next.model().popup());
    }
}
