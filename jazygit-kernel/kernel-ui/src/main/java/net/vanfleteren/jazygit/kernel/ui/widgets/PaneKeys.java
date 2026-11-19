package net.vanfleteren.jazygit.kernel.ui.widgets;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.state.view.Preview;

/**
 * What the panes on the left have in common: how they react to keys, and how they report a new highlight.
 */
public final class PaneKeys {

    private PaneKeys() {}

    /**
     * A key handler that does what {@code action} makes of a key, but only while {@code focused} says the pane
     * has the focus: a pane's handler also sees the keys typed in other panes.
     */
    public static KeyEventHandler whileFocused(BooleanSupplier focused, Function<KeyEvent, Option<Runnable>> action) {
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
    public static Option<Runnable> sending(Option<Msg> msg, Consumer<Msg> dispatch) {
        return msg.map(m -> () -> dispatch.accept(m));
    }

    /**
     * The message for highlighting {@code selected}, unless it is what {@code shown} already previews.
     */
    public static <K> Option<Msg> selectionChange(
            Option<K> selected, Option<? extends Preview<K, ?>> shown, Function<K, Msg> msg) {
        return selected.filter(key -> !shown.exists(preview -> preview.key().equals(key)))
                .map(msg);
    }

    /**
     * Whether the files pane of {@code model} is locked to the own list of the pane {@code pane}, which {@code Esc} in
     * it lets go of (ADR 149): not to a divergence or the commits of a branch shown in its place, where {@code Esc}
     * closes those.
     */
    public static boolean lockedTo(Model model, PaneId pane) {
        return model.repoView()
                .files()
                .locked()
                .exists(lock -> lock.pane().equals(pane) && lock.list() == FilesLock.In.LIST);
    }
}
