package net.vanfleteren.jazygit.kernel.ui;

import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;

/**
 * A popup of the app, with where the focus goes when it closes. The features list theirs, each in its
 * {@code *Dialogs}; at most one is open.
 *
 * @param returnTo the id of the pane that gets the focus back
 * @param dimsBehind whether what is behind the popup is dimmed while it is open, so it stands out
 */
public record PopupSlot(PopupView view, Function<Model, Focus.OnScreen> returnTo, boolean dimsBehind) {

    /**
     * A popup that gives the focus back to the pane {@code pane}, which it is opened from.
     */
    public static PopupSlot returningTo(PaneId pane, PopupView view) {
        return new PopupSlot(view, model -> pane, true);
    }

    /**
     * A popup that is opened from anywhere, and gives the focus back to where it was.
     */
    public static PopupSlot returningToFocus(PopupView view) {
        return new PopupSlot(view, PopupSlot::focusedPane, true);
    }

    /**
     * This popup with what is behind it left as it is, for one that previews its choices there.
     */
    public PopupSlot undimmed() {
        return new PopupSlot(view, returnTo, false);
    }

    /**
     * Where the focus was outside the popups on the screen shown, which a popup opened from anywhere gives it back
     * to, and which the screen gets back when it is shown again.
     */
    public static Focus.OnScreen focusedPane(Model model) {
        return model.shownPane().getOrElse(() -> model.screen().first());
    }
}
