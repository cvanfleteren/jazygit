package net.vanfleteren.jazygit.ui;

import java.util.function.Function;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * A popup of the app, with where the focus goes when it closes. The features list theirs, each in its
 * {@code *Dialogs}; at most one is open.
 *
 * @param returnTo the id of the pane that gets the focus back
 */
public record PopupSlot(PopupView view, Function<Model, String> returnTo) {

    /**
     * A popup that gives the focus back to the pane {@code paneId}, which it is opened from.
     */
    public static PopupSlot returningTo(String paneId, PopupView view) {
        return new PopupSlot(view, model -> paneId);
    }

    /**
     * A popup that is opened from anywhere, and gives the focus back to where it was.
     */
    public static PopupSlot returningToFocus(PopupView view) {
        return new PopupSlot(view, PopupSlot::focusedPane);
    }

    /**
     * Where the focus was outside the popups on the screen shown, which a popup opened from anywhere gives it back
     * to, and which the screen gets back when it is shown again.
     */
    public static String focusedPane(Model model) {
        return model.shownPane()
                .getOrElse(() -> model.screen() == Screen.PULL_REQUESTS
                        ? PullRequestsPanel.firstPane(model.prOverview())
                        : FilesPanel.ID);
    }
}
