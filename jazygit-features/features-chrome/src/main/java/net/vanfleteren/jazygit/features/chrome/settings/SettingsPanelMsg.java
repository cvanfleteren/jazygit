package net.vanfleteren.jazygit.features.chrome.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface SettingsPanelMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SettingsPanelUpdate.update(model, this);
    }

    /**
     * The user asked to see the settings.
     */
    record Requested() implements SettingsPanelMsg {}

    record ThemeRequested() implements SettingsPanelMsg {}

    /**
     * The user asked to choose the theme of the repository's own (ADR 70).
     */
    record RepositoryThemeRequested() implements SettingsPanelMsg {}

    /**
     * The user asked to open the settings file in git's editor (ADR 111).
     */
    record FileRequested() implements SettingsPanelMsg {}

    /**
     * The settings file, at {@code file}, read again as the panel opens, as {@code loaded} (ADR 111).
     */
    record FileRead(Option<String> file, SettingsStore.Loaded loaded) implements SettingsPanelMsg {}

    record FileNotRead(Failure failure) implements SettingsPanelMsg {}

    /**
     * The editor ran {@code commands} on the settings file, which reads as {@code loaded} once it is closed
     * (ADR 111).
     */
    record FileEdited(List<ExecutedCommand> commands, SettingsStore.Loaded loaded) implements SettingsPanelMsg {}

    record FileNotEdited(Failure failure) implements SettingsPanelMsg {}
}
