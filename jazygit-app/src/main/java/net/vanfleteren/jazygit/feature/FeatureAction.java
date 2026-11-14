package net.vanfleteren.jazygit.feature;

import io.vavr.collection.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * What an operation of a feature adds to the app: its keys, and its popups. Each operation declares its own, in
 * its subpackage, as {@code XxxAction.ACTION}; the feature lists them, in its {@link Feature}.
 *
 * @param keys   adds the operation's keys to a keymap
 * @param popups the operation's popups, sending what the user does to the dispatch they are given
 */
public record FeatureAction(UnaryOperator<Keymap> keys, Function<Consumer<Msg>, List<PopupSlot>> popups) {

    /**
     * An operation with keys, but no popups of its own.
     */
    public static FeatureAction ofKeys(UnaryOperator<Keymap> keys) {
        return new FeatureAction(keys, dispatch -> List.empty());
    }

    /**
     * An operation with popups, but no keys of its own: another operation leads to it.
     */
    public static FeatureAction ofPopups(Function<Consumer<Msg>, List<PopupSlot>> popups) {
        return new FeatureAction(UnaryOperator.identity(), popups);
    }
}
