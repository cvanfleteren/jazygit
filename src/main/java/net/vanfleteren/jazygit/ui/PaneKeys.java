package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Preview;

/**
 * What the panes on the left have in common: how they react to keys, and how they report a new highlight.
 */
final class PaneKeys {

    private PaneKeys() {}

    /**
     * A key handler that does what {@code action} makes of a key, but only while {@code focused} says the pane
     * has the focus: a pane's handler also sees the keys typed in other panes.
     */
    static KeyEventHandler whileFocused(BooleanSupplier focused, Function<KeyEvent, Option<Runnable>> action) {
        return event -> Option.when(focused.getAsBoolean(), event)
                .flatMap(action)
                .map(run -> {
                    run.run();
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * Sending {@code msg}, if there is one.
     */
    static Option<Runnable> sending(Option<Msg> msg, Consumer<Msg> dispatch) {
        return msg.map(m -> () -> dispatch.accept(m));
    }

    /**
     * The message for highlighting {@code selected}, unless it is what {@code shown} already previews.
     */
    static <K> Option<Msg> selectionChange(
            Option<K> selected, Option<? extends Preview<K, ?>> shown, Function<K, Msg> msg) {
        return selected.filter(key -> !shown.exists(preview -> preview.key().equals(key)))
                .map(msg);
    }
}
