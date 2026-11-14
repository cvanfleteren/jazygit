package net.vanfleteren.jazygit.feature;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * A feature, as the app knows it: its actions, which bring its keys and its popups. Each feature package declares
 * one, as {@code XxxFeature.FEATURE}, and {@link Features#ALL} lists it; that is all a new feature changes outside its
 * package (ADR 127).
 *
 * <p>Making the standard keymap makes every feature, so a feature does not ask for {@code Keymap.standard()} while its
 * class is initialised.
 *
 * @param actions the actions, in the order their keys are listed in a panel
 */
public record Feature(List<FeatureAction> actions) {

    /**
     * The feature of {@code actions}, in that order.
     */
    public static Feature of(FeatureAction... actions) {
        return new Feature(List.of(actions));
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
