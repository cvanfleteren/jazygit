package net.vanfleteren.jazygit.kernel.ui.settings;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.settings.SectionReads;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

/**
 * A section of the settings panel, as a feature declares it (ADR 163): its heading, its settings, one per row, each
 * changed by its key, and what it reads as the panel opens. The panel shows the sections one after the other, so it
 * names none of the features.
 *
 * @param headingKey the message key of its heading
 * @param rows       its settings, in order, each with a letter no other row of the panel has
 * @param onOpen     what it reads as the panel opens; the outcome goes to its {@code SectionState}
 */
public record SettingsSection(String headingKey, List<Choice<SettingsPanelPopup>> rows, SectionReads onOpen) {

    /**
     * A section of {@code rows} under the heading with the message key {@code headingKey}, which reads nothing.
     */
    @SafeVarargs
    public static SettingsSection of(String headingKey, Choice<SettingsPanelPopup>... rows) {
        return new SettingsSection(headingKey, List.of(rows), SectionReads.NONE);
    }

    /**
     * This section, reading what {@code onOpen} gives as the panel opens.
     */
    public SettingsSection reading(SectionReads onOpen) {
        return new SettingsSection(headingKey, rows, onOpen);
    }

    /**
     * Its rows as the panel shows them: the first under its heading.
     */
    public List<Choice<SettingsPanelPopup>> shown() {
        return rows.isEmpty() ? rows : rows.update(0, rows.head().withHeading(headingKey));
    }
}
