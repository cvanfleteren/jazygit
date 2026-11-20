package net.vanfleteren.jazygit.kernel.ui;

import io.vavr.collection.List;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Reactions.Reaction;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;

/**
 * A feature, as the app knows it: its actions, which bring its keys and its popups, what it shows in the content
 * panel, the settings it keeps, the sections it adds to the settings panel, what it does every so often, what it
 * answers of what happens in the kernel, and what it shows of a branch in the branches pane. Each feature package
 * declares one, as {@code XxxFeature.FEATURE}, and {@code Features.ALL} lists it; that is all a new feature changes
 * outside its package (ADR 127).
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
 * @param timers      what it dispatches every so often, without input
 * @param reactions   what it answers of what happens in the kernel, whichever part it keeps (ADR 158)
 * @param badges      what it shows of a branch in the branches pane, beside what git says (ADR 167)
 */
public record Feature(
        List<FeatureAction> actions,
        List<ContentSource> contents,
        List<SettingKey<?>> settingKeys,
        List<SettingsSection> settings,
        List<Timer> timers,
        List<Reaction> reactions,
        List<BranchBadges.Badge> badges) {

    /**
     * The feature of {@code actions}, in that order, which shows nothing in the content panel of its own, has no
     * settings, dispatches nothing every so often, answers nothing and shows nothing of a branch.
     */
    public static Feature of(FeatureAction... actions) {
        return new Feature(
                List.of(actions), List.empty(), List.empty(), List.empty(), List.empty(), List.empty(), List.empty());
    }

    /**
     * This feature, showing {@code contents} in the content panel as well, asked in that order.
     */
    public Feature showing(ContentSource... contents) {
        return new Feature(
                actions, this.contents.appendAll(List.of(contents)), settingKeys, settings, timers, reactions, badges);
    }

    /**
     * This feature, keeping the settings of {@code keys} in the settings file as well.
     */
    public Feature keeping(SettingKey<?>... keys) {
        return new Feature(
                actions, contents, settingKeys.appendAll(List.of(keys)), settings, timers, reactions, badges);
    }

    /**
     * This feature, adding {@code sections} to the settings panel as well, in that order.
     */
    public Feature withSettings(SettingsSection... sections) {
        return new Feature(
                actions, contents, settingKeys, settings.appendAll(List.of(sections)), timers, reactions, badges);
    }

    /**
     * This feature, also dispatching the message {@code msg} makes every {@code interval}: a read it does again, as
     * the message tells it to. The timers of the features are listed in the order of the features.
     */
    public Feature every(Duration interval, Supplier<Msg> msg) {
        return new Feature(
                actions, contents, settingKeys, settings, timers.append(new Timer(interval, msg)), reactions, badges);
    }

    /**
     * This feature, also answering what happens in the kernel with {@code reaction}: it is told each event, and
     * leaves the model as it is for those that are none of its concern.
     */
    public Feature reacting(Reaction reaction) {
        return new Feature(actions, contents, settingKeys, settings, timers, reactions.append(reaction), badges);
    }

    /**
     * This feature, also showing what {@code badge} makes of a branch in the branches pane, after the badges of the
     * features before it (ADR 167).
     */
    public Feature badging(BranchBadges.Badge badge) {
        return new Feature(actions, contents, settingKeys, settings, timers, reactions, badges.append(badge));
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

    /**
     * Dispatches what {@code msg} makes every {@code interval}.
     */
    public record Timer(Duration interval, Supplier<Msg> msg) {}
}
