package net.vanfleteren.jazygit.kernel.ui;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;

/**
 * A feature, as the app knows it: its actions, which bring its keys and its popups, what it shows in the content
 * panel, the settings it keeps and the sections it adds to the settings panel. Each feature package declares one, as
 * {@code XxxFeature.FEATURE}, and {@code Features.ALL} lists it; that is all a new feature changes outside its package
 * (ADR 127).
 *
 * <p>Making the standard keymap makes every feature, so a feature does not ask for {@code Keymap.standard()} while its
 * class is initialised.
 *
 * @param actions     the actions, in the order their keys are listed in a panel
 * @param contents    what the feature shows in the content panel, asked in this order, before the content panel's
 *                    own (ADR 154)
 * @param settingKeys the settings it keeps in the settings file, which the app reads and writes with the kernel's
 *                    (ADR 163)
 * @param settings    the sections it adds to the settings panel, in this order (ADR 163)
 */
public record Feature(
        List<FeatureAction> actions,
        List<ContentSource> contents,
        List<SettingKey<?>> settingKeys,
        List<SettingsSection> settings) {

    /**
     * The feature of {@code actions}, in that order, which shows nothing in the content panel of its own, and has no
     * settings.
     */
    public static Feature of(FeatureAction... actions) {
        return new Feature(List.of(actions), List.empty(), List.empty(), List.empty());
    }

    /**
     * This feature, showing {@code contents} in the content panel as well, asked in that order.
     */
    public Feature showing(ContentSource... contents) {
        return new Feature(actions, this.contents.appendAll(List.of(contents)), settingKeys, settings);
    }

    /**
     * This feature, keeping the settings of {@code keys} in the settings file as well.
     */
    public Feature keeping(SettingKey<?>... keys) {
        return new Feature(actions, contents, settingKeys.appendAll(List.of(keys)), settings);
    }

    /**
     * This feature, adding {@code sections} to the settings panel as well, in that order.
     */
    public Feature withSettings(SettingsSection... sections) {
        return new Feature(actions, contents, settingKeys, settings.appendAll(List.of(sections)));
    }

    /**
     * {@code keys} with the keys of this feature added, in the order of its actions.
     */
    public Keymap keys(Keymap keys) {
        return actions.foldLeft(keys, (keymap, action) -> action.keys().apply(keymap));
    }

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return actions.flatMap(action -> action.popups().apply(dispatch));
    }
}
