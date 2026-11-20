package net.vanfleteren.jazygit.app.keys;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.settings.SettingKeys;
import net.vanfleteren.jazygit.kernel.state.settings.PanelReads;
import net.vanfleteren.jazygit.kernel.state.settings.SectionReads;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSections;

/**
 * The settings of the features (ADR 163): the keys the settings file holds, the kernel's and each feature's, and the
 * sections the features add to the settings panel, in the order of {@link Features#ALL}, with what they read as it
 * opens. The sections are the {@link SettingsSections#standard()} ones, and their reads the
 * {@link PanelReads#standard()} ones, given as services, as the content panel's sources are.
 */
public final class StandardSettings implements SettingsSections.Provider, PanelReads.Provider {

    /**
     * The settings the settings file holds: the kernel's, then each feature's.
     */
    public static SettingKeys keys() {
        return SettingKeys.KERNEL.and(Features.ALL.flatMap(Feature::settingKeys));
    }

    @Override
    public List<SettingsSection> sections() {
        return Features.ALL.flatMap(Feature::settings);
    }

    @Override
    public List<SectionReads> reads() {
        return sections().map(SettingsSection::onOpen);
    }
}
